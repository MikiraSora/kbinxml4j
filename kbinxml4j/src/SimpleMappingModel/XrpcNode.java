package SimpleMappingModel;

import java.util.HashMap;
import java.util.Map;

public class XrpcNode {
    private String name = "";
    private Map<String, String> attributeMap = new HashMap<>();
    private Object content;

    public XrpcNode() {

    }

    public XrpcNode(String name, String typeName) {
        setName(name);
        setTypeAttr(typeName);
    }

    public XrpcNode(String name, String typeName, int size) {
        setName(name);
        setTypeAttr(typeName);
        setSizeAttr(size);
    }

    public XrpcNode(String name, String typeName, String contentString) {
        setName(name);
        setTypeAttr(typeName);
        setContentString(contentString);
    }

    public XrpcNode(String name, String typeName, int size, String contentString) {
        setName(name);
        setTypeAttr(typeName);
        setSizeAttr(size);
        setContentString(contentString);
    }

    public XrpcNode(String name, String typeName, XrpcNode[] children) {
        setName(name);
        setTypeAttr(typeName);
        setChildren(children);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null)
            name = "";
        this.name = name;
    }

    public Map<String, String> getAttributeMap() {
        return attributeMap;
    }

    public void setChildren(XrpcNode[] children) throws NullPointerException {
        if (children == null)
            throw new NullPointerException("children is null");
        content = children;
    }

    public void setContentString(String str) {
        if (str == null)
            str = "";
        content = str;
    }

    public String getContentString() {
        return (String) content;
    }

    public XrpcNode[] getChildren() throws Exception {
        if (content instanceof XrpcNode[] array)
            return array;
        throw new Exception("current content is not list<Node>");
    }

    public boolean hasChildren() {
        return content instanceof XrpcNode[];
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        try {
            buildString(sb);
        } catch (Exception e) {
            sb.append("<EXCEPTION>");
        }
        return sb.toString();
    }

    public void setTypeAttr(String typeName) {
        getAttributeMap().put("__type", typeName);
    }

    public String getTypeAttr() {
        return getAttributeMap().getOrDefault("__type", null);
    }

    public void setCountAttr(int count) {
        getAttributeMap().put("__count", String.valueOf(count));
    }

    public int getCountAttr() {
        return Integer.parseInt(getAttributeMap().getOrDefault("__count", "0"));
    }

    public void setSizeAttr(int size) {
        getAttributeMap().put("__size", String.valueOf(size));
    }

    public int getSizeAttr() {
        return Integer.parseInt(getAttributeMap().getOrDefault("__size", "0"));
    }

    private void buildString(StringBuilder sb) throws Exception {
        // 打印节点名和属性
        sb.append("<").append(name);
        if (!attributeMap.isEmpty()) {
            for (var entry : attributeMap.entrySet()) {
                sb.append(" ").append(entry.getKey())
                        .append("=\"").append(entry.getValue()).append("\"");
            }
        }

        if (hasChildren()) {
            sb.append(" Children: ");
            sb.append(getChildren().length);
        } else {
            sb.append(" Content: ");
            sb.append(getContentString());
        }
        sb.append(">");
    }
}
