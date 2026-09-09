package org.folio.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ModuleInfoTest {

  @Test
  void moduleVersionReturnsFilteredVersion() {
    // The actual /module-version.properties is Maven-filtered at build time.
    // Using the test-scoped valid fixture to verify the happy path independently.
    String version = ModuleInfo.moduleVersion("/test-module-version-valid.properties");
    assertThat(version, not(blankOrNullString()));
    assertThat(version, not(containsString("${")));
    assertThat(version, not(containsString("@")));
  }

  @Test
  void moduleVersionThrowsWhenResourceNotFound() {
    IllegalStateException ex = assertThrows(IllegalStateException.class,
      () -> ModuleInfo.moduleVersion("/nonexistent-resource-xyz.properties"));
    assertThat(ex.getMessage(), containsString("Missing module version resource"));
  }

  @Test
  void moduleVersionThrowsWhenVersionKeyAbsent() {
    IllegalStateException ex = assertThrows(IllegalStateException.class,
      () -> ModuleInfo.moduleVersion("/test-module-version-no-key.properties"));
    assertThat(ex.getMessage(), containsString("Invalid module version"));
  }

  @Test
  void moduleVersionThrowsWhenVersionIsBlank() {
    IllegalStateException ex = assertThrows(IllegalStateException.class,
      () -> ModuleInfo.moduleVersion("/test-module-version-blank.properties"));
    assertThat(ex.getMessage(), containsString("Invalid module version"));
  }

  @Test
  void moduleVersionThrowsWhenVersionContainsDollarPlaceholder() {
    IllegalStateException ex = assertThrows(IllegalStateException.class,
      () -> ModuleInfo.moduleVersion("/test-module-version-dollar.properties"));
    assertThat(ex.getMessage(), containsString("Invalid module version"));
  }

  @Test
  void moduleVersionThrowsWhenVersionContainsAtPlaceholder() {
    IllegalStateException ex = assertThrows(IllegalStateException.class,
      () -> ModuleInfo.moduleVersion("/test-module-version-at.properties"));
    assertThat(ex.getMessage(), containsString("Invalid module version"));
  }

  @Test
  void moduleVersionWrapsIoExceptionFromBrokenStream() {
    InputStream brokenStream = new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("Simulated read failure");
      }
    };

    try (MockedStatic<ModuleInfo> mocked = mockStatic(ModuleInfo.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
      mocked.when(() -> ModuleInfo.openResource(any())).thenReturn(brokenStream);
      UncheckedIOException ex = assertThrows(UncheckedIOException.class,
        () -> ModuleInfo.moduleVersion("/test-module-version-valid.properties"));
      assertThat(ex.getMessage(), containsString("Failed to read module version"));
    }
  }
}
