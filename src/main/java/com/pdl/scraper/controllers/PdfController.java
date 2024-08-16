package com.pdl.scraper.controllers;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.imageio.ImageIO;
import jakarta.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Controller
public class PdfController {

    private final ConcurrentMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @PostMapping("/convert")
    public void convert(@RequestParam("url") String url, HttpServletResponse response) throws Exception {
        Document doc = Jsoup.connect(url).get();
        String name = doc.select("div#honey").attr("title").trim();
        int id = Integer.parseInt(url.substring(url.indexOf("ID=") + 3, url.indexOf("&", url.indexOf("ID="))));
        int numberOfPages = Integer.parseInt(doc.select("form[name=searched] table.Displayable td:nth-of-type(4) b").text());

        // Create PDF document
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
                    bufferedImage = null;

                    // Notify frontend about progress
                    sendProgressUpdate(i, numberOfPages);
                }
            }

            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=" + name + ".pdf");
            document.save(response.getOutputStream());
        }
    }

    @GetMapping("/progress")
    public SseEmitter getProgress() {
        SseEmitter emitter = new SseEmitter();
        String emitterId = String.valueOf(emitter.hashCode());
        emitters.put(emitterId, emitter);

        emitter.onCompletion(() -> emitters.remove(emitterId));
        emitter.onTimeout(() -> emitters.remove(emitterId));

        return emitter;
    }

    private void sendProgressUpdate(int currentPage, int totalPages) {
        String message = "Pages " + currentPage + " of " + totalPages + " converted";
        emitters.forEach((id, emitter) -> {
            try {
                emitter.send(message, MediaType.TEXT_PLAIN);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
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
}