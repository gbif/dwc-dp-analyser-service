/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
