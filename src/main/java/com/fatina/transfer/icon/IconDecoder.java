package com.fatina.transfer.icon;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * 图标图片解码工具。
 * @author Fatina 2026/06/29
 */
public final class IconDecoder {
    private static final byte[] PNG_SIGNATURE = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};

    private IconDecoder() {
    }

    public static byte[] decodeToPng(byte[] rawData) throws IOException {
        if (isPng(rawData)) {
            return rawData;
        }
        BufferedImage image = readImage(rawData);
        if (image == null) {
            return null;
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            boolean written = ImageIO.write(image, "png", outputStream);
            if (!written || outputStream.size() == 0) {
                return null;
            }
            return outputStream.toByteArray();
        }
    }

    private static boolean isPng(byte[] rawData) {
        if (rawData == null || rawData.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (rawData[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private static BufferedImage readImage(byte[] rawData) throws IOException {
        try (ImageInputStream inputStream = ImageIO.createImageInputStream(new ByteArrayInputStream(rawData))) {
            if (inputStream == null) {
                return null;
            }

            Iterator<ImageReader> icnsReaders = ImageIO.getImageReadersByFormatName("icns");
            if (icnsReaders.hasNext()) {
                ImageReader reader = icnsReaders.next();
                try {
                    reader.setInput(inputStream);
                    return reader.read(0);
                } finally {
                    reader.dispose();
                }
            }
        }

        return ImageIO.read(new ByteArrayInputStream(rawData));
    }
}
