package org.folio.util;

import jakarta.validation.ValidationException;

import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class UuidHelperTest {
  private static final String VALID_UUID = "901d2ff8-7efb-4014-a9da-e1dc544402bc";
  private static final String INVALID_UUID = "901d2ff8-7efb-haha-a9da-e1dc544402bc";
  private static final String RANDOM_STRING = "not even close";
  private static final String EMPTY_STRING = "";

  public Object[] parametersForShouldPass() {
    return new Object[] {
      new Object[]{VALID_UUID, true},
      new Object[]{VALID_UUID, false},
      new Object[]{null, false}
    };
  }

  @Test
  @Parameters
  public void shouldPass(String uuid, boolean isRequired) {
    UuidHelper.validateUUID(uuid, isRequired);
  }

  public Object[] parametersForShouldFail() {
    return new Object[] {
      new Object[]{INVALID_UUID, true},
      new Object[]{INVALID_UUID, false},
      new Object[]{RANDOM_STRING, true},
      new Object[]{RANDOM_STRING, false},
      new Object[]{EMPTY_STRING, true},
      new Object[]{EMPTY_STRING, false},
      new Object[]{null, true}
    };
  }

  @Test(expected = ValidationException.class)
  @Parameters
  public void shouldFail(String uuid, boolean isRequired) {
    UuidHelper.validateUUID(uuid, isRequired);
  }

}
