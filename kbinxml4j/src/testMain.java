import SimpleMappingModel.XrpcNodeConverter;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Arrays;

public class testMain {
    public static void main(String[] args) throws Exception {
        // replicate Python CLI behaviour

        args = new String[]{
                "C:\\Users\\mikir\\Source\\Repos\\KBinXml.Net\\src\\Tests\\ManualTests\\data\\test.bin"
        };

        if (args.length < 1) {
            System.err.println("Usage: kbinxml file.[xml/bin] [--convert-illegal]");
            System.exit(1);
        }

        String filename = args[0];
        boolean convertIllegal = Arrays.asList(args).contains("--convert-illegal");

        try {
            byte[] input = readAllBytes(new File(filename));
            KbinXml xml = new KbinXml(input, convertIllegal);
            OutputStream out = System.out;
            if (KbinXml.isBinaryXml(input)) {
                String text = xml.toText();
                var xmlDoc= xml.getDocument();
                var node = XrpcNodeConverter.ConvertFromXml(xmlDoc);
                var xmlText = XrpcNodeConverter.ToXmlString(node);
                out.write(text.getBytes(Charset.forName("UTF-8")));
            } else {
                out.write(xml.toBinary());
            }
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (Exception bpe) {
            System.exit(141);
        }
    }

    private static byte[] readAllBytes(File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = fis.read(buf)) != -1) baos.write(buf, 0, r);
            return baos.toByteArray();
        }
    }
}
