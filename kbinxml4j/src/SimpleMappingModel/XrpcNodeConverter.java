package SimpleMappingModel;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class XrpcNodeConverter {
    public static XrpcNode ConvertFromXml(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("document is null");
        }

        return convertElement(document.getDocumentElement());
    }

    private static XrpcNode convertElement(Element element) {
        XrpcNode xrpcNode = new XrpcNode();
        xrpcNode.setName(element.getTagName());

        // 处理属性
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            org.w3c.dom.Node attr = attrs.item(i);
            xrpcNode.getAttributeMap().put(attr.getNodeName(), attr.getNodeValue());
        }

        // 处理子节点
        List<XrpcNode> children = new ArrayList<>();
        StringBuilder textContent = new StringBuilder();

        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            org.w3c.dom.Node domChild = childNodes.item(i);
            if (domChild.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                children.add(convertElement((Element) domChild));
            } else if (domChild.getNodeType() == org.w3c.dom.Node.TEXT_NODE) {
                String text = domChild.getNodeValue().trim();
                if (!text.isEmpty()) {
                    textContent.append(text);
                }
            }
        }

        // 判断是文本还是子节点
        if (!children.isEmpty()) {
            xrpcNode.setChildren(children.toArray(XrpcNode[]::new));
        } else {
            xrpcNode.setContentString(textContent.toString());
        }

        return xrpcNode;
    }

    public static Document ToXml(XrpcNode xrpcNode) throws Exception {
        if (xrpcNode == null) {
            throw new IllegalArgumentException("node is null");
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();

        // 转换根节点
        Element rootElement = createElement(document, xrpcNode);
        document.appendChild(rootElement);

        return document;
    }

    // 递归转换 Node -> Element
    private static Element createElement(Document doc, XrpcNode xrpcNode) throws Exception {
        Element element = doc.createElement(xrpcNode.getName());


        // 判断是 children 还是 text
        if (xrpcNode.hasChildren()) {
            var children = xrpcNode.getChildren();
            for (XrpcNode child : children) {
                Element childElement = createElement(doc, child);
                element.appendChild(childElement);
            }
        } else {
            String text = (String) xrpcNode.getContentString();
            if (text != null && !text.isEmpty()) {
                element.appendChild(doc.createTextNode(text));
            }
        }

        // 设置属性
        for (Map.Entry<String, String> entry : xrpcNode.getAttributeMap().entrySet()) {
            element.setAttribute(entry.getKey(), entry.getValue());
        }

        return element;
    }

    public static XrpcNode ConvertFromXmlString(String xml) throws Exception {
        if (xml == null || xml.isEmpty()) {
            throw new IllegalArgumentException("xml string is null or empty");
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringElementContentWhitespace(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));

        return convertElement(document.getDocumentElement());
    }

    public static String ToXmlString(XrpcNode xrpcNode) throws Exception {
        if (xrpcNode == null) {
            throw new IllegalArgumentException("node is null");
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();

        Element rootElement = createElement(document, xrpcNode);
        document.appendChild(rootElement);

        // 转换成字符串
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }
}
