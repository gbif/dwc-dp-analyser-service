package org.gbif.dp.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipUtils {

  private ZipUtils() {}

  /**
   * Unzips {@code zipFile} into {@code targetDir}, creating it if needed.
   * Existing files are overwritten.
   */
  public static void unzip(Path zipFile, Path targetDir) throws IOException {
    Files.createDirectories(targetDir);
    try (InputStream fis = Files.newInputStream(zipFile);
         ZipInputStream zis = new ZipInputStream(fis)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        Path resolved = targetDir.resolve(entry.getName()).normalize();
        // Guard against zip-slip
        if (!resolved.startsWith(targetDir)) {
          throw new IOException("Zip entry outside target dir: " + entry.getName());
        }
        if (entry.isDirectory()) {
          Files.createDirectories(resolved);
        } else {
          Files.createDirectories(resolved.getParent());
          Files.copy(zis, resolved, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        zis.closeEntry();
      }
    }
  }
}
