package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.exceptions.BadRequestException;
import org.generation.italy.demoxchange.security.AppUploadsProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Si occupa solo del file su disco: validazione tipo, scrittura, cancellazione.
 * Non conosce entita' ne' regole di dominio (quelle stanno in ItemImageService).
 */
@Service
public class ImageStorageService {

    private static final String PUBLIC_PREFIX = "/files/";

    private final Path root;

    public ImageStorageService(AppUploadsProperties props) {
        this.root = Paths.get(props.dir()).toAbsolutePath().normalize();
    }

    /**
     * Valida e salva il file, restituendo il path pubblico da persistere in ItemImage.url,
     * es. "/files/items/5/ab12cd34.jpg".
     */
    public String store(long itemId, MultipartFile file) {
        String ext = validateAndGetExtension(file);
        String filename = UUID.randomUUID() + "." + ext;
        Path itemDir = root.resolve("items").resolve(String.valueOf(itemId)).normalize();

        try {
            Files.createDirectories(itemDir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, itemDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store image", e);
        }
        return PUBLIC_PREFIX + "items/" + itemId + "/" + filename;
    }

    /** Cancella il file corrispondente a un path pubblico /files/... . No-op se il path e' estraneo o il file non c'e'. */
    public void delete(String publicUrl) {
        if (publicUrl == null || !publicUrl.startsWith(PUBLIC_PREFIX)) {
            return;
        }
        Path target = root.resolve(publicUrl.substring(PUBLIC_PREFIX.length())).normalize();
        if (!target.startsWith(root)) {
            return; // guardia contro path traversal
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete image", e);
        }
    }

    /**
     * Determina l'estensione dai byte reali del file (magic numbers), non dal
     * Content-Type dichiarato dal client, che e' facilmente falsificabile.
     */
    private String validateAndGetExtension(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("invalid_file", "File is empty");
        }

        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(12);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read image", e);
        }

        String ext = detectExtension(header);
        if (ext == null) {
            throw new BadRequestException("invalid_file", "Unsupported or invalid image file");
        }
        return ext;
    }

    private static String detectExtension(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (h.length >= 8
                && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) {
            return "png";
        }
        if (h.length >= 12
                && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
