import org.w3c.dom.*;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

public class KbinXml {
    // debug flags
    public static boolean DEBUG_OFFSETS = false;
    public static boolean DEBUG = true;

    public static final int SIGNATURE = 0xA0;
    public static final int SIG_COMPRESSED = 0x42;
    public static final int SIG_UNCOMPRESSED = 0x45;

    public static final String XML_ENCODING = "UTF-8";
    public static final String BIN_ENCODING = "cp932"; // windows shift-jis variant

    // NOTE: these names are Python codec names; just kept for mapping
    public static final Map<Integer, String> encoding_strings;
    public static final Map<String, Integer> encoding_vals;

    static {
        Map<Integer, String> tmp = new HashMap<>();
        tmp.put(0x00, "cp932");
        tmp.put(0x20, "ASCII");
        tmp.put(0x40, "ISO-8859-1");
        tmp.put(0x60, "EUC_JP");
        tmp.put(0x80, "cp932");
        tmp.put(0xA0, "UTF-8");
        encoding_strings = Collections.unmodifiableMap(tmp);

        Map<String, Integer> vals = new HashMap<>();
        for (Map.Entry<Integer, String> e : encoding_strings.entrySet()) {
            vals.put(e.getValue(), e.getKey());
        }
        // ensure cp932 maps to 0x80 (like the python code)
        vals.put("cp932", 0x80);
        encoding_vals = Collections.unmodifiableMap(vals);
    }

    private static void debugPrint(String s) {
        if (DEBUG) System.out.println(s);
    }

    public static class KBinException extends RuntimeException {
        public KBinException(String msg) {
            super(msg);
        }

        public KBinException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    // Fields similar to Python class
    private boolean convertIllegalThings;
    private Document xmlDoc; // DOM Document root container; we will keep Document and root element
    private Element xmlRoot; // actual root element (similar to etree.getroot())
    private String encoding;
    private boolean compressed;
    private Integer dataSize;

    private KBinXmlByteBuffer nodeBuf;
    private KBinXmlByteBuffer dataBuf;
    private KBinXmlByteBuffer dataByteBuf;
    private KBinXmlByteBuffer dataWordBuf;

    // helper message for user
    private static String convertIllegalHelp = "set convert_illegal_things=True in the KBinXML constructor";

    // ---------- Constructors ----------

    /**
     * Construct from raw bytes (either binary kbin or xml text bytes) or from an existing DOM Element/Document.
     * The Python code accepted etree._Element / etree._ElementTree or bytes.
     *
     * @param input                either byte[] (file content) or org.w3c.dom.Document or org.w3c.dom.Element
     * @param convertIllegalThings same semantics as Python
     */
    public KbinXml(Object input, boolean convertIllegalThings) {
        this.convertIllegalThings = convertIllegalThings;

        try {
            if (input instanceof Element) {
                // wrap into Document
                Element el = (Element) input;
                Document doc = el.getOwnerDocument();
                if (doc == null) {
                    // New document
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    doc = db.newDocument();
                    Node imported = doc.importNode(el, true);
                    doc.appendChild(imported);
                }
                this.xmlDoc = doc;
                this.xmlRoot = doc.getDocumentElement();
            } else if (input instanceof Document) {
                this.xmlDoc = (Document) input;
                this.xmlRoot = this.xmlDoc.getDocumentElement();
            } else if (input instanceof byte[]) {
                byte[] bytes = (byte[]) input;
                if (isBinaryXml(bytes)) {
                    fromBinary(bytes);
                } else {
                    fromText(bytes);
                }
            } else {
                throw new IllegalArgumentException("Unsupported input type for KBinXML constructor");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public KbinXml(Object input) {
        this(input, false);
    }

    // ---------- Text serialization / parsing ----------

    public String toText() {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty(OutputKeys.ENCODING, XML_ENCODING);
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            StringWriter sw = new StringWriter();
            t.transform(new DOMSource(xmlDoc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize XML", e);
        }
    }

    public Document getDocument() {
        return xmlDoc;
    }

    private void fromText(byte[] input) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            // keep namespace aware so we can handle namespaces later
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            ByteArrayInputStream bais = new ByteArrayInputStream(input);
            Document doc = db.parse(bais);
            this.xmlDoc = doc;
            this.xmlRoot = doc.getDocumentElement();
            this.encoding = XML_ENCODING;
            this.compressed = true;
            this.dataSize = null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse XML text input", e);
        }
    }

    // ---------- Utility: detect binary XML ----------

    public static boolean isBinaryXml(byte[] input) {
        if (input.length < 2) return false;
        KBinXmlByteBuffer nodeBuf = new KBinXmlByteBuffer(input);
        int b0 = nodeBuf.getU8();
        int b1 = nodeBuf.getU8();
        return b0 == SIGNATURE && (b1 == SIG_COMPRESSED || b1 == SIG_UNCOMPRESSED);
    }

    // ---------- Helpers for mem size calculation ----------

    // small implementation approximating Python's struct.calcsize for format strings used in XML_FORMATS
    // In Python code they sometimes use fmt["type"] * varCount i.e. string repetition, so we handle repeated letters
    private static int calcSize(String fmt) {
        if (fmt == null) return 0;
        int size = 0;
        // if fmt contains numbers or other tokens, try to parse simply:
        // We'll sum per-character sizes for letters, and ignore other characters.
        for (char c : fmt.toCharArray()) {
            switch (c) {
                case 'b':
                case 'B':
                case 'c':
                case 's':
                    size += 1;
                    break;
                case 'h':
                case 'H':
                    size += 2;
                    break;
                case 'i':
                case 'I':
                case 'l':
                case 'L':
                    size += 4;
                    break;
                case 'q':
                case 'Q':
                case 'd': // q = 8 bytes, d = double = 8
                    size += 8;
                    break;
                case 'f': // float
                    size += 4;
                    break;
                default:
                    // ignore other symbols (including endianness markers)
                    break;
            }
        }
        if (size == 0) {
            // fallback: try to parse integer at end of string, e.g. "4s"
            try {
                String digits = fmt.replaceAll("\\D+", "");
                if (!digits.isEmpty()) {
                    size = Integer.parseInt(digits);
                }
            } catch (Exception ignored) {
            }
        }
        return size;
    }

    // ---------- Properties similar to Python ----------

    // _data_mem_size property
    public int get_data_mem_size() {
        int data_len = 0;
        // traverse all elements
        List<Element> all = iterElements(xmlRoot);
        for (Element e : all) {
            String t = e.getAttribute("__type");
            if (t == null || t.isEmpty()) continue;

            String countStr = e.getAttribute("__count");
            int count = 1;
            if (countStr != null && !countStr.isEmpty()) {
                try {
                    count = Integer.parseInt(countStr);
                } catch (Exception ex) {
                    count = 1;
                }
            }
            String sizeStr = e.getAttribute("__size");
            int size = 1;
            if (sizeStr != null && !sizeStr.isEmpty()) {
                try {
                    size = Integer.parseInt(sizeStr);
                } catch (Exception ex) {
                    size = 1;
                }
            }

            Integer id = FormatIds.XML_TYPES.get(t);
            if (id == null) continue;
            @SuppressWarnings("unchecked")
            FormatIds.XmlFormat x = FormatIds.XML_FORMATS.get(id);
            if (x == null) continue;

            int m = 0;
            int xcount = x.count;
            String xname = (String) x.name;
            if (xcount > 0) {
                m = xcount * calcSize((String) x.type) * count * size;
            } else if ("bin".equals(xname)) {
                String text = e.getNodeValue();
                if (text == null) text = "";
                m = text.length() / 2;
            } else {
                String text = e.getNodeValue();
                if (text == null) text = "";
                try {
                    m = text.getBytes(Charset.forName(this.encoding)).length + 1; // null terminator
                } catch (Exception ex) {
                    m = text.getBytes().length + 1;
                }
            }

            if (m <= 4) continue;

            if ("bin".equals(xname)) {
                data_len += (m + 1) & ~1;
            } else {
                data_len += (m + 3) & ~3;
            }
        }
        return data_len;
    }

    public int get_mem_size() {
        int data_len = get_data_mem_size();
        List<Element> all = iterElements(xmlRoot);
        int node_count = all.size();

        int size;
        if (this.compressed) {
            size = 52 * node_count + data_len + 630;
        } else {
            int tags_len = 0;
            for (Element e : all) {
                int e_len = Math.max(e.getTagName().length(), 8);
                e_len = (e_len + 3) & ~3;
                tags_len += e_len;
            }
            size = 56 * node_count + data_len + 630 + tags_len;
        }

        return (size + 8) & ~7;
    }

    // ---------- data buffer helpers ----------

    // Python data_grab_auto
    public byte[] data_grab_auto() {
        int size = this.dataBuf.getS32();
        byte[] ret = this.dataBuf.getBytes(size);
        this.dataBuf.realignReads(4);
        return ret;
    }

    public void data_append_auto(byte[] data) {
        this.dataBuf.appendS32(data.length);
        this.dataBuf.appendBytes(data);
        this.dataBuf.realignWrites(4);
    }

    public String data_grab_string() {
        byte[] data = data_grab_auto();
        if (data.length == 0) return "";
        // remove trailing null
        byte[] trimmed = Arrays.copyOf(data, data.length - 1);
        try {
            return new String(trimmed, Charset.forName(this.encoding));
        } catch (Exception e) {
            if ("cp932".equalsIgnoreCase(this.encoding)) {
                if (!this.convertIllegalThings) {
                    throw new KBinException(String.format("Could not decode string. To force utf8 decode %s.", convertIllegalHelp), e);
                }
                System.err.println("KBinXML: Malformed Shift-JIS string found, attempting UTF-8 decode");
                System.err.println("KBinXML: Raw string data: " + Arrays.toString(trimmed));
                return new String(trimmed, Charset.forName("UTF-8"));
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    public void data_append_string(String string) {
        try {
            byte[] b = (string.getBytes(this.encoding));
            byte[] out = Arrays.copyOf(b, b.length + 1);
            out[b.length] = 0;
            data_append_auto(out);
        } catch (UnsupportedEncodingException e) {
            byte[] b = string.getBytes();
            byte[] out = Arrays.copyOf(b, b.length + 1);
            out[b.length] = 0;
            data_append_auto(out);
        }
    }

    // data_grab_aligned and data_append_aligned require careful offset handling to emulate Python behaviour
    public Object data_grab_aligned(String type, int count) {
        if (this.dataByteBuf.getOffset() % 4 == 0) {
            this.dataByteBuf.setOffset(this.dataBuf.getOffset());
        }
        if (this.dataWordBuf.getOffset() % 4 == 0) {
            this.dataWordBuf.setOffset(this.dataBuf.getOffset());
        }
        int size = calcSize(type) * count;
        Object ret;
        if (size == 1) {
            ret = this.dataByteBuf.get(type, count);
        } else if (size == 2) {
            ret = this.dataWordBuf.get(type, count);
        } else {
            ret = this.dataBuf.get(type, count);
            this.dataBuf.realignReads(4);
        }
        int trailing = Math.max(this.dataByteBuf.getOffset(), this.dataWordBuf.getOffset());
        if (this.dataBuf.getOffset() < trailing) {
            this.dataBuf.setOffset(trailing);
            this.dataBuf.realignReads(4);
        }
        return ret;
    }

    public void data_append_aligned(Object data, String type, int count) {
        if (this.dataByteBuf.getOffset() % 4 == 0) {
            this.dataByteBuf.setOffset(this.dataBuf.getOffset());
        }
        if (this.dataWordBuf.getOffset() % 4 == 0) {
            this.dataWordBuf.setOffset(this.dataBuf.getOffset());
        }
        int size = calcSize(type) * count;
        if (size == 1) {
            if (this.dataByteBuf.getOffset() % 4 == 0) {
                this.dataBuf.appendU32(0);
            }
            this.dataByteBuf.set(data, this.dataByteBuf.getOffset(), type, count);
        } else if (size == 2) {
            if (this.dataWordBuf.getOffset() % 4 == 0) {
                this.dataBuf.appendU32(0);
            }
            this.dataWordBuf.set(data, this.dataWordBuf.getOffset(), type, count);
        } else {
            this.dataBuf.append(data, type, count);
            this.dataBuf.realignWrites(4);
        }
    }

    // ---------- node name handling ----------

    public void append_node_name(String name) {
        if (this.compressed) {
            Sixbit.packSixBit(name, this.nodeBuf);
        } else {
            try {
                byte[] enc = name.getBytes(this.encoding);
                // Python: nodeBuf.appendU8((len(enc) - 1) | 64)
                this.nodeBuf.appendU8(((enc.length - 1) & 0xFF) | 64);
                this.nodeBuf.appendBytes(enc);
            } catch (UnsupportedEncodingException e) {
                byte[] enc = name.getBytes();
                this.nodeBuf.appendU8(((enc.length - 1) & 0xFF) | 64);
                this.nodeBuf.appendBytes(enc);
            }
        }
    }

    // ---------- namespace helper (approx) ----------
    // Python uses lxml's nsmap; here we emulate by adding xmlns attributes on an element
    private Element _add_namespace(Element node, String name, String value) {
        // copy existing attributes and children into a new element with a namespace declaration
        Document doc = node.getOwnerDocument();
        // create a new element with same tag name (no namespace change) then add xmlns:prefix attr
        Element newNode = doc.createElement(node.getTagName());
        // copy attributes
        NamedNodeMap attrs = node.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr a = (Attr) attrs.item(i);
            newNode.setAttribute(a.getName(), a.getValue());
        }
        // add namespace declaration
        newNode.setAttribute("xmlns:" + name, value);

        // copy children
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node c = children.item(i);
            Node imported = doc.importNode(c, true);
            newNode.appendChild(imported);
        }

        Node parent = node.getParentNode();
        if (parent != null) {
            parent.replaceChild(newNode, node);
        } else {
            // no parent: replace root
            doc.replaceChild(newNode, node);
        }

        return newNode;
    }

    // ---------- node -> binary (serialization) ----------
    private void _node_to_binary(Element node) {
        String nodeType = node.getAttribute("__type");
        if (nodeType == null || nodeType.isEmpty()) {
            String text = node.getNodeValue();
            if (text != null && text.trim().length() > 0) {
                nodeType = "str";
            } else {
                nodeType = "void";
            }
        }
        Integer nodeId = FormatIds.XML_TYPES.get(nodeType);
        if (nodeId == null) {
            throw new KBinException("Unknown node type: " + nodeType);
        }

        int isArray = 0;
        String countAttr = node.getAttribute("__count");
        int count = 0;
        if (countAttr != null && !countAttr.isEmpty()) {
            try {
                count = Integer.parseInt(countAttr);
                isArray = 64; // array flag bit
            } catch (Exception ignored) {
            }
        }

        // nodeBuf.appendU8(nodeId | isArray)
        this.nodeBuf.appendU8((nodeId | isArray) & 0xFF);

        String name = node.getTagName();
        append_node_name(name);

        if (!"void".equals(nodeType)) {
            @SuppressWarnings("unchecked")
            var fmt = FormatIds.XML_FORMATS.get(nodeId);
            if (fmt == null) throw new KBinException("Missing format for nodeId " + nodeId);

            String val = node.getNodeValue();
            if (val == null && node.getFirstChild() != null)
                val = node.getFirstChild().getNodeValue();
            byte[] dataBytes = null;
            Object dataObj = null;

            String fmtName = fmt.name;
            if ("bin".equals(fmtName)) {
                if (val == null) val = "";
                // hex string to bytes
                String hex = val.trim();
                if (hex.length() % 2 == 1) hex = "0" + hex;
                int len = hex.length() / 2;
                dataBytes = new byte[len];
                for (int i = 0; i < len; i++) {
                    dataBytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
                }
            } else if ("str".equals(fmtName)) {
                if (val == null) val = "";
                try {
                    byte[] enc = (val.getBytes(this.encoding));
                    dataBytes = Arrays.copyOf(enc, enc.length + 1);
                    dataBytes[enc.length] = 0;
                } catch (UnsupportedEncodingException e) {
                    byte[] enc = val.getBytes();
                    dataBytes = Arrays.copyOf(enc, enc.length + 1);
                    dataBytes[enc.length] = 0;
                }
            } else {
                // numbers / arrays: split and map with fmt.get("fromStr", int)
                if (val == null) val = "";
                String[] parts = val.trim().isEmpty() ? new String[0] : val.trim().split("\\s+");
                // We'll store them as a List of Numbers or Strings depending on fmt.get("fromStr")
                java.util.List<Object> list = new ArrayList<>();
                @SuppressWarnings("unchecked")
                var fromStr = fmt.fromStr;
                for (String p : parts) {
                    if (fromStr != null) {
                        // Not likely; skip
                        var d = fromStr.apply(p);
                        list.add(d);
                    } else {
                        try {
                            list.add(Integer.parseInt(p));
                        } catch (NumberFormatException nfe) {
                            try {
                                list.add(Double.parseDouble(p));
                            } catch (NumberFormatException ex) {
                                list.add(p);
                            }
                        }
                    }
                }
                dataObj = list;
                if (count != 0) {
                    int fmtCount = fmt.count;
                    if (fmtCount > 0) {
                        if (list.size() / fmtCount != count) {
                            throw new IllegalArgumentException("Array length does not match __count attribute");
                        }
                    }
                }
            }

            // append to data buffer depending on array flags
            Integer fmtCount = fmt.count;
            String fmtType = (String) fmt.type;

            if (isArray != 0 || fmtCount == -1) {
                if (dataBytes != null) {
                    this.dataBuf.appendU32(dataBytes.length);
                    this.dataBuf.appendBytes(dataBytes);
                } else if (dataObj != null) {
                    // dataObj is List<Object>, use KBinXmlByteBuffer.append
                    java.util.List<?> list = (java.util.List<?>) dataObj;
                    this.dataBuf.appendU32(list.size() * calcSize(fmtType));
                    this.dataBuf.append(list, fmtType, list.size());
                }
                this.dataBuf.realignWrites(4);
            } else {
                if (dataBytes != null) {
                    // convert bytes to array data and append appropriately
                    // Here we treat it as raw bytes; Python code would call data_append_aligned for strings: it expects to write bytes into buffer aligned
                    // We'll call data_append_aligned with a byte[] and type "B"
                    this.data_append_aligned(dataBytes, fmtType, fmtCount);
                } else if (dataObj != null) {
                    this.data_append_aligned(dataObj, fmtType, fmtCount);
                }
            }
        }

        // for test consistency and to be more faithful, sort the attrs
        NamedNodeMap attrs = node.getAttributes();
        List<Map.Entry<String, String>> sortedAttrs = new ArrayList<>();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr a = (Attr) attrs.item(i);
            sortedAttrs.add(new AbstractMap.SimpleEntry<>(a.getName(), a.getValue()));
        }
        sortedAttrs.sort(Comparator.comparing(Map.Entry::getKey));

        for (Map.Entry<String, String> kv : sortedAttrs) {
            String key = kv.getKey();
            String value = kv.getValue();
            if (!key.equals("__type") && !key.equals("__size") && !key.equals("__count")) {
                data_append_string(value);
                int attrType = FormatIds.XML_TYPES.get("attr");
                this.nodeBuf.appendU8(attrType);
                append_node_name(key);
            }
        }

        // children
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node c = children.item(i);
            if (c.getNodeType() == Node.ELEMENT_NODE) {
                _node_to_binary((Element) c);
            }
        }

        // always has isArray bit set on nodeEnd
        int nodeEndType = FormatIds.XML_TYPES.get("nodeEnd");
        this.nodeBuf.appendU8(nodeEndType | 64);
    }

    // ---------- to_binary (serialize whole XML to kbin binary) ----------
    public byte[] toBinary() {
        return toBinary(BIN_ENCODING, false);
    }

    public byte[] toBinary(String encoding, boolean compressed) {
        this.encoding = encoding;
        this.compressed = compressed;

        KBinXmlByteBuffer header = new KBinXmlByteBuffer();
        header.appendU8(SIGNATURE);
        if (this.compressed) {
            header.appendU8(SIG_COMPRESSED);
        } else {
            header.appendU8(SIG_UNCOMPRESSED);
        }
        header.appendU8(encoding_vals.getOrDefault(this.encoding, 0) & 0xFF);
        header.appendU8((0xFF ^ encoding_vals.getOrDefault(this.encoding, 0)) & 0xFF);

        this.nodeBuf = new KBinXmlByteBuffer();
        this.dataBuf = new KBinXmlByteBuffer();
        this.dataByteBuf = new KBinXmlByteBuffer(this.dataBuf.getData());
        this.dataWordBuf = new KBinXmlByteBuffer(this.dataBuf.getData());

        // recursive convert nodes
        _node_to_binary(this.xmlRoot);

        // endSection with isArray bit
        int endSection = FormatIds.XML_TYPES.get("endSection");
        this.nodeBuf.appendU8(endSection | 64);
        this.nodeBuf.realignWrites(4);

        header.appendU32(this.nodeBuf.length()); // assume KBinXmlByteBuffer has length() or data length; if not adjust
        this.dataSize = this.dataBuf.length();
        this.nodeBuf.appendU32(this.dataSize);

        // combine all bytes: header.data + nodeBuf.data + dataBuf.data
        byte[] combined = new byte[header.getData().length + this.nodeBuf.getData().length + this.dataBuf.getData().length];
        int pos = 0;
        System.arraycopy(header.getData(), 0, combined, pos, header.getData().length);
        pos += header.getData().length;
        System.arraycopy(this.nodeBuf.getData(), 0, combined, pos, this.nodeBuf.getData().length);
        pos += this.nodeBuf.getData().length;
        System.arraycopy(this.dataBuf.getData(), 0, combined, pos, this.dataBuf.getData().length);
        return combined;
    }

    // ---------- from_binary (parse bytes to DOM) ----------
    public void fromBinary(byte[] input) {
        try {
            // create root wrapper
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();
            Element wrapper = doc.createElement("root");
            doc.appendChild(wrapper);
            this.xmlDoc = doc;
            this.xmlRoot = wrapper;

            this.nodeBuf = new KBinXmlByteBuffer(input);
            if (!(this.nodeBuf.getU8() == SIGNATURE))
                throw new Exception("assert failed: this.nodeBuf.getU8() == SIGNATURE");

            int compress = this.nodeBuf.getU8();
            if (!(compress == SIG_COMPRESSED || compress == SIG_UNCOMPRESSED))
                throw new Exception("assert failed: compress == SIG_COMPRESSED || compress == SIG_UNCOMPRESSED");
            this.compressed = (compress == SIG_COMPRESSED);

            int encoding_key = this.nodeBuf.getU8();
            if (!(this.nodeBuf.getU8() == (0xFF ^ encoding_key)))
                throw new Exception("assert failed: this.nodeBuf.getU8() == (0xFF ^ encoding_key)");
            this.encoding = encoding_strings.getOrDefault(encoding_key, XML_ENCODING);

            int nodeEnd = this.nodeBuf.getU32() + 8;
            this.nodeBuf.setEnd(nodeEnd);

            this.dataBuf = new KBinXmlByteBuffer(input, nodeEnd);
            this.dataSize = this.dataBuf.getU32();

            this.dataByteBuf = new KBinXmlByteBuffer(input, nodeEnd);
            this.dataWordBuf = new KBinXmlByteBuffer(input, nodeEnd);

            boolean nodesLeft = true;
            Element node = this.xmlRoot;

            while (nodesLeft && this.nodeBuf.hasData()) {
                while (this.nodeBuf.peekU8() == 0) {
                    debugPrint("Skipping 0 node ID");
                    this.nodeBuf.getU8();
                }

                int nodeType = this.nodeBuf.getU8();
                int isArray = nodeType & 64;
                nodeType &= ~64;

                var nodeFormat = FormatIds.XML_FORMATS.get(nodeType);
                if (nodeFormat == null)
                    nodeFormat = new FormatIds.XmlFormat(0, null, 0, new String[]{"Unknown"}, null, null);
                debugPrint("Node type is " + nodeFormat.name + " (" + nodeType + ")");

                String name = "";
                if (nodeType != FormatIds.XML_TYPES.get("nodeEnd") && nodeType != FormatIds.XML_TYPES.get("endSection")) {
                    if (this.compressed) {
                        name = Sixbit.unpackSixbit(this.nodeBuf);
                    } else {
                        int length = (this.nodeBuf.getU8() & ~64) + 1;
                        byte[] nb = this.nodeBuf.getBytes(length);
                        name = new String(nb, Charset.forName(this.encoding));
                    }
                    debugPrint(name);
                }

                boolean skip = true;

                if (nodeType == FormatIds.XML_TYPES.get("attr")) {
                    String value = data_grab_string();
                    if (name.startsWith("xmlns:")) {
                        String[] parts = name.split(":", 2);
                        if (parts.length == 2) {
                            String prefix = parts[1];
                            node = _add_namespace(node, prefix, value);
                        }
                    } else if (name.contains(":")) {
                        String[] parts = name.split(":", 2);
                        String prefix = parts[0];
                        String local = parts[1];
                        // find namespace URI declared on this element or ancestors
                        String nsUri = findNamespaceUri(node, prefix);
                        if (nsUri != null) {
                            node.setAttributeNS(nsUri, name, value);
                        } else {
                            node.setAttribute(name, value);
                        }
                    } else {
                        node.setAttribute(name, value);
                    }
                } else if (nodeType == FormatIds.XML_TYPES.get("nodeEnd")) {
                    if (node.getParentNode() != null && node.getParentNode().getNodeType() == Node.ELEMENT_NODE) {
                        node = (Element) node.getParentNode();
                    }
                } else if (nodeType == FormatIds.XML_TYPES.get("endSection")) {
                    nodesLeft = false;
                } else if (!FormatIds.XML_FORMATS.containsKey(nodeType)) {
                    throw new UnsupportedOperationException("Implement node " + nodeType);
                } else {
                    skip = false;
                }

                if (skip) continue;

                // create child element under current node
                Element child;
                try {
                    child = this.xmlDoc.createElement(name);
                    node.appendChild(child);
                } catch (DOMException e) {
                    String fixedName = "_" + name;
                    if (this.convertIllegalThings) {
                        child = this.xmlDoc.createElement(fixedName);
                        node.appendChild(child);
                    } else {
                        throw new KBinException(String.format("Could not create node with name \"%s\". To rename it to \"%s\", %s.", name, fixedName, convertIllegalHelp), e);
                    }
                }
                node = child;

                if (nodeType == FormatIds.XML_TYPES.get("nodeStart")) {
                    continue;
                }

                node.setAttribute("__type", (String) nodeFormat.name);

                long varCount = nodeFormat.count;
                long arrayCount = 1;
                if (varCount == -1) {
                    varCount = this.dataBuf.getU32();
                    isArray = 1;
                } else if (isArray != 0) {
                    long raw = this.dataBuf.getU32();
                    int sizeOf = calcSize(((String) nodeFormat.type).repeat(Math.max(1, (int) varCount)));
                    arrayCount = raw / sizeOf;
                    node.setAttribute("__count", Integer.toString((int) arrayCount));
                }
                long totalCount = arrayCount * varCount;

                Object data;
                if (isArray != 0) {
                    data = this.dataBuf.get((String) nodeFormat.type, (int) totalCount);
                    this.dataBuf.realignReads(4);
                } else {
                    data = this.data_grab_aligned((String) nodeFormat.type, (int) totalCount);
                }

                String stringVal;
                if (nodeType == FormatIds.XML_TYPES.get("binary")) {
                    node.setAttribute("__size", Integer.toString((int) totalCount));
                    // data is expected to be byte[] or int[]; convert to hex
                    if (data instanceof byte[]) {
                        byte[] db2 = (byte[]) data;
                        StringBuilder sb = new StringBuilder();
                        for (byte b : db2) sb.append(String.format("%02x", b & 0xFF));
                        stringVal = sb.toString();
                    } else {
                        stringVal = "";
                    }
                } else if (nodeType == FormatIds.XML_TYPES.get("string")) {
                    // data assumed to be byte[] with trailing null
                    if (data instanceof byte[]) {
                        var db2 = (byte[]) data;
                        if (db2.length > 0) {
                            byte[] trimmed = Arrays.copyOf(db2, Math.max(0, db2.length - 1));
                            stringVal = new String(trimmed, Charset.forName(this.encoding));
                        } else {
                            stringVal = "";
                        }
                    } else {
                        stringVal = "";
                    }
                } else {
                    // join using toStr if available; fallback to toString
                    @SuppressWarnings("unchecked")
                    java.util.function.Function<Object, String> toStr = nodeFormat.toStr;
                    if (toStr == null)
                        toStr = (o) -> o != null ? String.valueOf(o) : "";
                    if (data.getClass().isArray()) {
                        var arr = CastToArray(data);
                        stringVal = Arrays.stream(arr).map(toStr::apply).collect(Collectors.joining(" "));
                    } else {
                        stringVal = data == null ? "" : String.valueOf(data);
                    }
                }

                node.setTextContent(stringVal.replaceAll("\0+$", ""));
            }

            // because we need the 'real' root (Python returns xml_doc[0])
            // our wrapper root child at index 0 is the real root
            NodeList wrapperChildren = this.xmlRoot.getChildNodes();
            for (int i = 0; i < wrapperChildren.getLength(); i++) {
                Node nd = wrapperChildren.item(i);
                if (nd.getNodeType() == Node.ELEMENT_NODE) {
                    this.xmlRoot = (Element) nd;
                    this.xmlDoc.removeChild(wrapper); // optional, keep actual document root consistent
                    this.xmlDoc.appendChild(this.xmlRoot);
                    break;
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse binary input", e);
        }
    }

    // ---------- Utilities ----------

    private static Object[] CastToArray(Object arrayOrObject) {
        if (arrayOrObject == null)
            return new Object[0];
        if (!arrayOrObject.getClass().isArray())
            return new Object[]{arrayOrObject};
        var length = Array.getLength(arrayOrObject);
        var arr = new Object[length];
        for (int i = 0; i < length; i++) {
            arr[i] = Array.get(arrayOrObject, i);
        }
        return arr;
    }

    // iterate through Elements (preorder)
    private static List<Element> iterElements(Element root) {
        List<Element> list = new ArrayList<>();
        if (root == null) return list;
        traverseElements(root, list);
        return list;
    }

    private static void traverseElements(Node node, List<Element> acc) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            acc.add((Element) node);
            NodeList nl = node.getChildNodes();
            for (int i = 0; i < nl.getLength(); i++) {
                traverseElements(nl.item(i), acc);
            }
        }
    }

    // find namespace URI declared in ancestors for prefix (simple lookup of xmlns:prefix attributes)
    private static String findNamespaceUri(Element node, String prefix) {
        Node cur = node;
        while (cur != null && cur.getNodeType() == Node.ELEMENT_NODE) {
            Element el = (Element) cur;
            String attr = el.getAttribute("xmlns:" + prefix);
            if (attr != null && !attr.isEmpty()) return attr;
            cur = cur.getParentNode();
        }
        return null;
    }
}
