package com.fatina.transfer.icon;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.IconFace;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * APK 图标提取策略。
 * @author Fatina 2026/06/29
 */
public class ApkIconExtractor implements PackageIconExtractor {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".apk");

    @Override
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    @Override
    public IconExtractionResult extract(File file, IconExtractorConfig config) throws Exception {
        if (!config.apkEnabled()) {
            return null;
        }

        try (ApkFile apkFile = new ApkFile(file)) {
            List<ApkIconCandidate> candidates = new ArrayList<>();
            IconFace primaryIcon = apkFile.getIconFile();
            if (primaryIcon != null && primaryIcon.getData() != null && primaryIcon.getData().length > 0) {
                candidates.add(createCandidate(primaryIcon.getData(), 100));
            }
            for (IconFace iconFace : apkFile.getAllIcons()) {
                if (iconFace == null || iconFace.getData() == null || iconFace.getData().length == 0) {
                    continue;
                }
                candidates.add(createCandidate(iconFace.getData(), 10));
            }

            candidates.sort(Comparator
                    .comparingInt(ApkIconCandidate::score).reversed()
                    .thenComparingInt(ApkIconCandidate::area).reversed());

            for (ApkIconCandidate candidate : candidates) {
                if (candidate.rawBytes() == null || candidate.rawBytes().length == 0) {
                    continue;
                }
                byte[] pngBytes = IconDecoder.decodeToPng(candidate.rawBytes());
                if (pngBytes == null || pngBytes.length == 0) {
                    continue;
                }
                return new IconExtractionResult(pngBytes, "image/png");
            }
        }
        return null;
    }

    private ApkIconCandidate createCandidate(byte[] rawBytes, int baseScore) {
        int area = resolveArea(rawBytes);
        return new ApkIconCandidate(rawBytes, baseScore + area, area);
    }

    private int resolveArea(byte[] rawBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(rawBytes));
            if (image == null) {
                return 0;
            }
            return image.getWidth() * image.getHeight();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private record ApkIconCandidate(byte[] rawBytes, int score, int area) {
    }
}
