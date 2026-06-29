package com.fatina.transfer.preview;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Set;

/**
 * PSD 预览提取策略。
 * @author Fatina 2026/06/29
 */
public class PsdPreviewExtractor implements FilePreviewExtractor {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".psd");

    @Override
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    @Override
    public FilePreviewResult extract(File file, PreviewConfig config) throws Exception {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            return null;
        }

        BufferedImage normalizedImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = normalizedImage.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            boolean written = ImageIO.write(normalizedImage, "png", outputStream);
            if (!written || outputStream.size() == 0) {
                return null;
            }
            return new FilePreviewResult(outputStream.toByteArray(), "image/png");
        }
    }
}
