package com.fatina.transfer.icon;

import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * DMG 图标提取策略。
 * @author Fatina 2026/06/29
 */
public class DmgIconExtractor implements PackageIconExtractor {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".dmg");
    private static volatile boolean dmgExtractionAvailable = true;
    private static volatile boolean dmgFailureLogged = false;

    @Override
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    @Override
    public IconExtractionResult extract(File file, IconExtractorConfig config) throws Exception {
        if (!config.dmgEnabled() || !dmgExtractionAvailable) {
            return null;
        }

        try {
            Files.createDirectories(config.nativeTempDir());
            System.setProperty("java.io.tmpdir", config.nativeTempDir().toString());
            try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
                 IInArchive archive = SevenZip.openInArchive(null, new RandomAccessFileInStream(randomAccessFile))) {
                List<ArchiveEntryCandidate> candidates = collectCandidates(archive);
                for (ArchiveEntryCandidate candidate : candidates) {
                    byte[] iconBytes = extractEntryBytes(archive, candidate.index());
                    if (iconBytes == null || iconBytes.length == 0) {
                        continue;
                    }
                    byte[] pngBytes = IconDecoder.decodeToPng(iconBytes);
                    if (pngBytes != null && pngBytes.length > 0) {
                        return new IconExtractionResult(pngBytes, "image/png");
                    }
                }
            }
        } catch (Throwable e) {
            dmgExtractionAvailable = false;
            if (!dmgFailureLogged) {
                dmgFailureLogged = true;
                System.err.println("DMG 图标提取能力不可用，已自动降级为类型图标: " + e.getMessage());
            }
            return null;
        }

        return null;
    }

    private List<ArchiveEntryCandidate> collectCandidates(IInArchive archive) throws Exception {
        List<ArchiveEntryCandidate> candidates = new ArrayList<>();
        int itemCount = archive.getNumberOfItems();
        for (int index = 0; index < itemCount; index++) {
            Object folder = archive.getProperty(index, PropID.IS_FOLDER);
            if (Boolean.TRUE.equals(folder)) {
                continue;
            }

            Object pathProperty = archive.getProperty(index, PropID.PATH);
            if (!(pathProperty instanceof String rawPath)) {
                continue;
            }

            String normalizedPath = rawPath.replace('\\', '/');
            String lowerPath = normalizedPath.toLowerCase(Locale.ROOT);
            if (!lowerPath.endsWith(".icns")) {
                continue;
            }

            candidates.add(new ArchiveEntryCandidate(index, normalizedPath, score(lowerPath)));
        }

        candidates.sort(Comparator.comparingInt(ArchiveEntryCandidate::score).reversed());
        return candidates;
    }

    private int score(String lowerPath) {
        int score = 0;
        if (lowerPath.contains(".app/contents/resources/")) {
            score += 100;
        }
        if (lowerPath.endsWith("/app.icns")) {
            score += 80;
        }
        if (lowerPath.endsWith("/electron.icns")) {
            score += 60;
        }
        if (lowerPath.contains("resources")) {
            score += 30;
        }
        if (lowerPath.endsWith("/.volumeicon.icns")) {
            score += 20;
        }
        return score;
    }

    private byte[] extractEntryBytes(IInArchive archive, int index) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ISequentialOutStream outStream = data -> {
            try {
                outputStream.write(data);
            } catch (IOException e) {
                throw new IllegalStateException("写入 DMG 图标缓存流失败", e);
            }
            return data.length;
        };

        ExtractOperationResult result = archive.extractSlow(index, outStream);
        if (result != ExtractOperationResult.OK) {
            return null;
        }
        return outputStream.toByteArray();
    }

    private record ArchiveEntryCandidate(int index, String path, int score) {
    }
}
