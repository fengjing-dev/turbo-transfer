package com.fatina.transfer.preview;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Set;

/**
 * PDF 首屏预览提取策略。
 * @author Fatina 2026/06/29
 */
public class PdfPreviewExtractor implements FilePreviewExtractor {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".pdf");

    @Override
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    @Override
    public FilePreviewResult extract(File file, PreviewConfig config) throws Exception {
        try (PDDocument document = Loader.loadPDF(file)) {
            if (document.getNumberOfPages() == 0) {
                return null;
            }

            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(0, 120, ImageType.RGB);
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", outputStream);
                return new FilePreviewResult(outputStream.toByteArray(), "image/png");
            }
        }
    }
}
