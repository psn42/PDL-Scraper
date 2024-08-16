package com.pdl.scraper.controllers;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;

@Controller
@ResponseBody
public class PdfController {

    @PostMapping("/convert")
    public ResponseEntity<?> convert(@RequestParam("url") String url) {
        try {
            Document doc = Jsoup.connect(url).get();
            String name = doc.select("div#honey").attr("title").trim();
            int id = Integer.parseInt(url.substring(url.indexOf("ID=") + 3, url.indexOf("&", url.indexOf("ID="))));
            int numberOfPages = Integer.parseInt(doc.select("form[name=searched] table.Displayable td:nth-of-type(4) b").text());

            try (PDDocument document = new PDDocument()) {
                for (int i = 1; i <= numberOfPages; i++) {
                    String imageUrl = constructImageUrl(id, i);

                    BufferedImage bufferedImage;
                    try (InputStream in = new URL(imageUrl).openStream()) {
                        bufferedImage = ImageIO.read(in);
                    }

                    if (bufferedImage != null) {
                        float width = bufferedImage.getWidth();
                        float height = bufferedImage.getHeight();
                        PDRectangle pageSize = new PDRectangle(width, height);

                        PDPage page = new PDPage(pageSize);
                        document.addPage(page);

                        PDImageXObject pdImage = PDImageXObject.createFromByteArray(document, imageToByteArray(bufferedImage), imageUrl);
                        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                            contentStream.drawImage(pdImage, 0, 0, width, height);
                        }
                    }
                }

                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                document.save(byteArrayOutputStream);
                byte[] pdfData = byteArrayOutputStream.toByteArray();

                String base64Pdf = Base64.getEncoder().encodeToString(pdfData);

                return ResponseEntity.ok().body(new PdfResponse(name, base64Pdf));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error generating PDF: " + e.getMessage());
        }
    }

    private String constructImageUrl(int id, int page) {
        return "http://www.panjabdigilib.org/images?ID=" + id + "&page=" + page + "&pagetype=1&Searched=1234";
    }

    private byte[] imageToByteArray(BufferedImage bufferedImage) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(bufferedImage, "jpg", baos);
            return baos.toByteArray();
        }
    }

    private static class PdfResponse {
        private String name;
        private String base64Pdf;

        public PdfResponse(String name, String base64Pdf) {
            this.name = name;
            this.base64Pdf = base64Pdf;
        }

        public String getName() {
            return name;
        }

        public String getBase64Pdf() {
            return base64Pdf;
        }
    }
}
