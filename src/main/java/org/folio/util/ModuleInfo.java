package org.folio.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ModuleInfo {

  private static final String MODULE_VERSION_RESOURCE = "/module-version.properties";

  public static String moduleVersion() {
    return moduleVersion(MODULE_VERSION_RESOURCE);
  }

  static String moduleVersion(String resourcePath) {
    try (var stream = openResource(resourcePath)) {
      if (stream == null) {
        throw new IllegalStateException("Missing module version resource: " + resourcePath);
      }
      var properties = new Properties();
      properties.load(stream);
      String version = properties.getProperty("version");
      if (version == null || version.isBlank() || version.contains("${") || version.contains("@")) {
        throw new IllegalStateException(
          "Invalid module version in " + resourcePath + ": " + version);
      }
      return version;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read module version from " + resourcePath, e);
    }
  }

  static InputStream openResource(String resourcePath) {
    return ModuleInfo.class.getResourceAsStream(resourcePath);
  }
}
