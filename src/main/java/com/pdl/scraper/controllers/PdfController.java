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
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.pdl.scraper.DocumentDetails;

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

    @PostMapping("/details")
    public String go(@RequestParam String url, Model model) throws Exception {
        DocumentDetails details = getDetails(url);
        if (details == null) {
            model.addAttribute("errorMessage", "Invalid URL. Please try again.");
            return "index";
        }
        model.addAttribute("details", details);
        return "details";
    }

    @PostMapping("/convert")
    public void convert(@RequestBody DocumentDetails details, HttpServletResponse response) throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (int i = 1; i <= details.getNumberOfPages(); i++) {
                String imageUrl = constructImageUrl(details.getId(), i);
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
                    sendProgressUpdate(i, details.getNumberOfPages());
                }
            }

            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=" + details.getName() + ".pdf");
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

    private DocumentDetails getDetails(String url) throws Exception {
        String newUrl = url;
        if (url.contains("displayPageContent")) {
            newUrl = url.replace("Content", "");
        }
        if (!url.contains("displayPage")) {
            return null;
        }
        Document doc = Jsoup.connect(newUrl).get();
        int id = Integer.parseInt(newUrl.substring(newUrl.indexOf("ID=") + 3, newUrl.indexOf("&", newUrl.indexOf("ID="))));
        String name = doc.select("div#honey").attr("name").trim();
        int numberOfPages = Integer.parseInt(doc.select("td[width='131']:contains(Pages) + td").text().trim());
        String thumbnailUrl = doc.select("img[width='200'][height='200']").attr("src");
        String thumbnail = "http://www.panjabdigilib.org/" + thumbnailUrl.substring(thumbnailUrl.indexOf("pdl")).trim();
        return new DocumentDetails(id, name, numberOfPages, thumbnail);
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