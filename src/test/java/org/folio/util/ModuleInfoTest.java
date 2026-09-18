package org.folio.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class ModuleInfoTest {

  @Test
  void moduleVersionReturnsVersion() {
    try (MockedStatic<ModuleInfo> mocked = mockStatic(ModuleInfo.class, CALLS_REAL_METHODS)) {
      mocked.when(() -> ModuleInfo.openResource(any())).thenReturn(streamOf("version=1.2.3"));
      assertThat(ModuleInfo.moduleVersion("/irrelevant.properties"), is("1.2.3"));
    }
  }

  @Test
  void moduleVersionThrowsWhenResourceNotFound() {
    IllegalStateException ex = assertThrows(IllegalStateException.class,
      () -> ModuleInfo.moduleVersion("/nonexistent-resource-xyz.properties"));
    assertThat(ex.getMessage(), containsString("Missing module version resource"));
  }

  @Test
  void moduleVersionThrowsWhenVersionKeyAbsent() {
    try (MockedStatic<ModuleInfo> mocked = mockStatic(ModuleInfo.class, CALLS_REAL_METHODS)) {
      mocked.when(() -> ModuleInfo.openResource(any())).thenReturn(streamOf("# no version key"));
      IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> ModuleInfo.moduleVersion("/irrelevant.properties"));
      assertThat(ex.getMessage(), containsString("Invalid module version"));
    }
  }

  @ParameterizedTest(name = "version=\"{0}\"")
  @ValueSource(strings = {"", "   ", "${project.version}", "@project.version@"})
  void moduleVersionThrowsWhenVersionIsInvalid(String versionValue) {
    try (MockedStatic<ModuleInfo> mocked = mockStatic(ModuleInfo.class, CALLS_REAL_METHODS)) {
      mocked.when(() -> ModuleInfo.openResource(any())).thenReturn(streamOf("version=" + versionValue));
      IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> ModuleInfo.moduleVersion("/irrelevant.properties"));
      assertThat(ex.getMessage(), containsString("Invalid module version"));
    }
  }

  @Test
  void moduleVersionWrapsIoExceptionFromBrokenStream() {
    InputStream brokenStream = new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("Simulated read failure");
      }
    };

    try (MockedStatic<ModuleInfo> mocked = mockStatic(ModuleInfo.class, CALLS_REAL_METHODS)) {
      mocked.when(() -> ModuleInfo.openResource(any())).thenReturn(brokenStream);
      UncheckedIOException ex = assertThrows(UncheckedIOException.class,
        () -> ModuleInfo.moduleVersion("/irrelevant.properties"));
      assertThat(ex.getMessage(), containsString("Failed to read module version"));
    }
  }

  private static InputStream streamOf(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}
