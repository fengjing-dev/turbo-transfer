package com.fatina.transfer.icon;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Set;
import javax.imageio.ImageIO;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.platform.win32.WinNT;

/**
 * Windows 安装包图标提取策略，直接从包体资源里枚举图标并优先选择最大尺寸。
 * @author Fatina 2026/06/29
 */
public class WindowsPackageIconExtractor implements PackageIconExtractor {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".exe", ".msi");

    @Override
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    @Override
    public IconExtractionResult extract(File file, IconExtractorConfig config) throws Exception {
        if (!file.exists() || !file.isFile()) {
            return null;
        }

        int iconCount = Shell32.INSTANCE.ExtractIconEx(file.getAbsolutePath(), -1, null, null, 0);
        if (iconCount <= 0) {
            return null;
        }

        WinDef.HICON[] largeIcons = new WinDef.HICON[iconCount];
        WinDef.HICON[] smallIcons = new WinDef.HICON[iconCount];
        int extractedCount = Shell32.INSTANCE.ExtractIconEx(file.getAbsolutePath(), 0, largeIcons, smallIcons, iconCount);
        if (extractedCount <= 0) {
            destroyIcons(largeIcons);
            destroyIcons(smallIcons);
            return null;
        }

        BufferedImage largestImage = null;
        try {
            largestImage = selectLargestImage(largeIcons, smallIcons);
        } finally {
            destroyIcons(largeIcons);
            destroyIcons(smallIcons);
        }

        if (largestImage == null) {
            return null;
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            boolean written = ImageIO.write(largestImage, "png", outputStream);
            if (!written || outputStream.size() == 0) {
                return null;
            }
            return new IconExtractionResult(outputStream.toByteArray(), "image/png");
        }
    }

    private BufferedImage selectLargestImage(WinDef.HICON[] largeIcons, WinDef.HICON[] smallIcons) {
        BufferedImage largestImage = null;
        int largestArea = -1;
        largestImage = chooseLargestFromSet(largeIcons, largestImage, largestArea);
        if (largestImage != null) {
            largestArea = largestImage.getWidth() * largestImage.getHeight();
        }
        return chooseLargestFromSet(smallIcons, largestImage, largestArea);
    }

    private BufferedImage chooseLargestFromSet(WinDef.HICON[] icons, BufferedImage currentLargest, int currentLargestArea) {
        if (icons == null) {
            return currentLargest;
        }

        BufferedImage largestImage = currentLargest;
        int largestArea = currentLargestArea;
        for (WinDef.HICON icon : icons) {
            if (icon == null) {
                continue;
            }
            BufferedImage image = convertIconToImage(icon);
            if (image == null) {
                continue;
            }
            int area = image.getWidth() * image.getHeight();
            if (area > largestArea) {
                largestArea = area;
                largestImage = image;
            }
        }
        return largestImage;
    }

    private BufferedImage convertIconToImage(WinDef.HICON icon) {
        WinGDI.ICONINFO iconInfo = new WinGDI.ICONINFO();
        if (!User32.INSTANCE.GetIconInfo(icon, iconInfo)) {
            return null;
        }

        try {
            WinDef.HBITMAP colorBitmap = iconInfo.hbmColor != null ? iconInfo.hbmColor : iconInfo.hbmMask;
            if (colorBitmap == null) {
                return null;
            }

            WinGDI.BITMAP bitmap = new WinGDI.BITMAP();
            int result = GDI32.INSTANCE.GetObject(colorBitmap, bitmap.size(), bitmap.getPointer());
            bitmap.read();
            if (result == 0) {
                return null;
            }

            int width = bitmap.bmWidth.intValue();
            int height = Math.abs(bitmap.bmHeight.intValue());
            if (width <= 0 || height <= 0) {
                return null;
            }
            if (iconInfo.hbmColor == null) {
                height = height / 2;
            }

            WinDef.HDC screenDc = User32.INSTANCE.GetDC(null);
            if (screenDc == null) {
                return null;
            }

            WinDef.HDC memoryDc = GDI32.INSTANCE.CreateCompatibleDC(screenDc);
            if (memoryDc == null) {
                User32.INSTANCE.ReleaseDC(null, screenDc);
                return null;
            }

            WinNT.HANDLE oldBitmap = GDI32.INSTANCE.SelectObject(memoryDc, colorBitmap);
            try {
                WinGDI.BITMAPINFO bitmapInfo = new WinGDI.BITMAPINFO();
                bitmapInfo.bmiHeader.biSize = bitmapInfo.bmiHeader.size();
                bitmapInfo.bmiHeader.biWidth = width;
                bitmapInfo.bmiHeader.biHeight = -height;
                bitmapInfo.bmiHeader.biPlanes = 1;
                bitmapInfo.bmiHeader.biBitCount = 32;
                bitmapInfo.bmiHeader.biCompression = WinGDI.BI_RGB;

                Memory buffer = new Memory((long) width * height * 4);
                int rows = GDI32.INSTANCE.GetDIBits(memoryDc, colorBitmap, 0, height, buffer, bitmapInfo, WinGDI.DIB_RGB_COLORS);
                if (rows == 0) {
                    return null;
                }

                boolean[] transparencyMask = readTransparencyMask(iconInfo.hbmMask, width, height, screenDc);
                return toBufferedImage(buffer, width, height, transparencyMask);
            } finally {
                GDI32.INSTANCE.SelectObject(memoryDc, oldBitmap);
                GDI32.INSTANCE.DeleteDC(memoryDc);
                User32.INSTANCE.ReleaseDC(null, screenDc);
            }
        } finally {
            if (iconInfo.hbmColor != null) {
                GDI32.INSTANCE.DeleteObject(iconInfo.hbmColor);
            }
            if (iconInfo.hbmMask != null) {
                GDI32.INSTANCE.DeleteObject(iconInfo.hbmMask);
            }
        }
    }

    private BufferedImage toBufferedImage(Memory buffer, int width, int height, boolean[] transparencyMask) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[width * height];
        int stride = 4;
        boolean hasMeaningfulAlpha = false;
        for (int i = 0; i < pixels.length; i++) {
            int offset = i * stride;
            int blue = buffer.getByte(offset) & 0xFF;
            int green = buffer.getByte(offset + 1) & 0xFF;
            int red = buffer.getByte(offset + 2) & 0xFF;
            int alpha = buffer.getByte(offset + 3) & 0xFF;
            if (alpha > 0 && alpha < 255) {
                hasMeaningfulAlpha = true;
            }
            pixels[i] = (alpha << 24) | (red << 16) | (green << 8) | blue;
        }

        if (!hasMeaningfulAlpha && transparencyMask != null) {
            for (int i = 0; i < pixels.length; i++) {
                int alpha = transparencyMask[i] ? 0 : 255;
                pixels[i] = (alpha << 24) | (pixels[i] & 0x00FFFFFF);
            }
        } else if (!hasMeaningfulAlpha) {
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = (0xFF << 24) | (pixels[i] & 0x00FFFFFF);
            }
        }

        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    private boolean[] readTransparencyMask(WinDef.HBITMAP maskBitmap, int width, int height, WinDef.HDC screenDc) {
        if (maskBitmap == null) {
            return null;
        }

        WinDef.HDC memoryDc = GDI32.INSTANCE.CreateCompatibleDC(screenDc);
        if (memoryDc == null) {
            return null;
        }

        WinNT.HANDLE oldBitmap = GDI32.INSTANCE.SelectObject(memoryDc, maskBitmap);
        try {
            WinGDI.BITMAPINFO bitmapInfo = new WinGDI.BITMAPINFO();
            bitmapInfo.bmiHeader.biSize = bitmapInfo.bmiHeader.size();
            bitmapInfo.bmiHeader.biWidth = width;
            bitmapInfo.bmiHeader.biHeight = -height;
            bitmapInfo.bmiHeader.biPlanes = 1;
            bitmapInfo.bmiHeader.biBitCount = 1;
            bitmapInfo.bmiHeader.biCompression = WinGDI.BI_RGB;

            int rowStride = ((width + 31) / 32) * 4;
            Memory buffer = new Memory((long) rowStride * height);
            int rows = GDI32.INSTANCE.GetDIBits(memoryDc, maskBitmap, 0, height, buffer, bitmapInfo, WinGDI.DIB_RGB_COLORS);
            if (rows == 0) {
                return null;
            }

            boolean[] transparencyMask = new boolean[width * height];
            for (int y = 0; y < height; y++) {
                int rowOffset = y * rowStride;
                for (int x = 0; x < width; x++) {
                    int byteIndex = rowOffset + (x / 8);
                    int bitMask = 0x80 >> (x % 8);
                    transparencyMask[y * width + x] = (buffer.getByte(byteIndex) & bitMask) != 0;
                }
            }
            return transparencyMask;
        } finally {
            GDI32.INSTANCE.SelectObject(memoryDc, oldBitmap);
            GDI32.INSTANCE.DeleteDC(memoryDc);
        }
    }

    private void destroyIcons(WinDef.HICON[] icons) {
        if (icons == null) {
            return;
        }
        for (WinDef.HICON icon : icons) {
            if (icon != null) {
                User32.INSTANCE.DestroyIcon(icon);
            }
        }
    }
}
