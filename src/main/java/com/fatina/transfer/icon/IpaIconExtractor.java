package com.fatina.transfer.icon;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * iOS 安装包图标提取策略。
 * @author Fatina 2026/06/29
 */
public class IpaIconExtractor implements PackageIconExtractor {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".ipa");

    @Override
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    @Override
    public IconExtractionResult extract(File file, IconExtractorConfig config) throws Exception {
        try (ZipFile zipFile = new ZipFile(file)) {
            List<IpaIconCandidate> candidates = collectCandidates(zipFile);
            for (IpaIconCandidate candidate : candidates) {
                ZipEntry entry = zipFile.getEntry(candidate.path());
                if (entry == null || entry.isDirectory()) {
                    continue;
                }
                try (InputStream inputStream = zipFile.getInputStream(entry)) {
                    byte[] rawBytes = inputStream.readAllBytes();
                    byte[] pngBytes = IconDecoder.decodeToPng(rawBytes);
                    if (pngBytes == null || pngBytes.length == 0) {
                        continue;
                    }
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
                    if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                        continue;
                    }
                    return new IconExtractionResult(pngBytes, "image/png");
                }
            }
        }
        return null;
    }

    private List<IpaIconCandidate> collectCandidates(ZipFile zipFile) {
        List<IpaIconCandidate> candidates = new ArrayList<>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }

            String path = entry.getName();
            String lowerPath = path.toLowerCase(Locale.ROOT);
            if (!lowerPath.startsWith("payload/") || !lowerPath.endsWith(".png")) {
                continue;
            }
            if (!lowerPath.contains(".app/")) {
                continue;
            }
            if (!lowerPath.contains("appicon") && !lowerPath.contains("icon")) {
                continue;
            }

            candidates.add(new IpaIconCandidate(path, score(lowerPath), estimateArea(lowerPath)));
        }

        candidates.sort(Comparator
                .comparingInt(IpaIconCandidate::score).reversed()
                .thenComparingInt(IpaIconCandidate::area).reversed());
        return candidates;
    }

    private int score(String lowerPath) {
        int score = 0;
        if (lowerPath.contains("appicon")) {
            score += 100;
        }
        if (lowerPath.contains("ios-marketing")) {
            score += 80;
        }
        if (lowerPath.contains("@3x")) {
            score += 60;
        } else if (lowerPath.contains("@2x")) {
            score += 40;
        }
        if (lowerPath.contains("60x60") || lowerPath.contains("76x76") || lowerPath.contains("83.5x83.5")) {
            score += 30;
        }
        return score;
    }

    private int estimateArea(String lowerPath) {
        int bestSide = 0;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{2,4})(?:\\.\\d+)?x(\\d{2,4})").matcher(lowerPath);
        while (matcher.find()) {
            int width = Integer.parseInt(matcher.group(1));
            int height = Integer.parseInt(matcher.group(2));
            bestSide = Math.max(bestSide, width * height);
        }
        if (lowerPath.contains("1024")) {
            bestSide = Math.max(bestSide, 1024 * 1024);
        }
        return bestSide;
    }

    private record IpaIconCandidate(String path, int score, int area) {
    }
}
