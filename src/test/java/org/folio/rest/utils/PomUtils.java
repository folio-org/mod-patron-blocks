package org.folio.rest.utils;

import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

public class PomUtils {

  public static String getModuleVersion() {
    try {
      Document doc = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(new File("pom.xml"));
      // The first <version> element in the POM is the project version
      return doc.getElementsByTagName("version").item(0).getTextContent();
    } catch (Exception e) {
      throw new RuntimeException("Failed to read version from pom.xml", e);
    }
  }
}
