/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.testFramework.codeInsight.hierarchy;

import com.intellij.ide.hierarchy.newAPI.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.newAPI.HierarchyTreeStructure;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static junit.framework.TestCase.assertEquals;

public class HierarchyViewTestFixture {
  private static final String NODE_ELEMENT_NAME = "node";
  private static final String ANY_NODES_ELEMENT_NAME = "any";
  private static final String TEXT_ATTR_NAME = "text";
  private static final String BASE_ATTR_NAME = "base";

  /**
   * Check the tree structure against the expected.
   * @param treeStructure tree structure to check
   * @param expectedStructure an expected structure in XML format.
   *                          If you load expected XML structure from the file,
   *                          better to use {@link #doHierarchyTest(HierarchyTreeStructure, File)} instead.
   */
  public static void doHierarchyTest(@NotNull HierarchyTreeStructure treeStructure,
                                     @NotNull String expectedStructure) {
    doHierarchyTest(treeStructure, expectedStructure, null);
  }

  /**
   * Check the tree structure against the expected.
   * @param treeStructure tree structure to check
   * @param expectedFile an XML file containing expected structure
   * @throws IOException if expectedFile reading failed
   * @throws FileComparisonFailure if content doesn't match
   */
  public static void doHierarchyTest(@NotNull HierarchyTreeStructure treeStructure,
                                     @NotNull File expectedFile) throws IOException {
    doHierarchyTest(treeStructure, FileUtil.loadFile(expectedFile), expectedFile);
  }

  private static void doHierarchyTest(@NotNull HierarchyTreeStructure treeStructure,
                                      @NotNull String expectedStructure,
                                      @Nullable File expectedFile) {
    try {
      checkHierarchyTreeStructure(treeStructure, JDOMUtil.load(expectedStructure));
    }
    catch (Throwable e) {
      String actual = dump(treeStructure, null, 0);
      if (!expectedStructure.equals(actual)) {
        throw new FileComparisonFailure("XML structure comparison for your convenience, actual failure details BELOW",
                                        expectedStructure, actual,
                                        expectedFile == null ? null : expectedFile.getAbsolutePath());
      }
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }
  }

  @NotNull
  public static String dump(@NotNull HierarchyTreeStructure treeStructure,
                            @Nullable HierarchyNodeDescriptor descriptor,
                            int level) {
    StringBuilder s = new StringBuilder();
    dump(treeStructure, descriptor, level, s);
    return s.toString();
  }

  private static void dump(@NotNull HierarchyTreeStructure treeStructure,
                           @Nullable HierarchyNodeDescriptor descriptor,
                           int level,
                           @NotNull StringBuilder b) {
    if (level > 10) {
      for (int i = 0; i < level; i++) b.append("  ");
      b.append("<Probably infinite part skipped>\n");
      return;
    }
    if (descriptor == null) descriptor = (HierarchyNodeDescriptor)treeStructure.getRootElement();
    for (int i = 0; i < level; i++) b.append("  ");
    descriptor.update();
    b.append("<node text=\"").append(descriptor.getHighlightedText().getText()).append("\"")
      .append(treeStructure.getBaseDescriptor() == descriptor ? " base=\"true\"" : "");

    Object[] children = treeStructure.getChildElements(descriptor);
    if (children.length > 0) {
      b.append(">\n");
      for (Object o : children) {
        HierarchyNodeDescriptor d = (HierarchyNodeDescriptor)o;
        dump(treeStructure, d, level + 1, b);
      }
      for (int i = 0; i < level; i++) b.append("  ");
      b.append("</node>\n");
    }
    else {
      b.append("/>\n");
    }
  }

  private static void checkHierarchyTreeStructure(@NotNull HierarchyTreeStructure treeStructure,
                                                  @Nullable Element rootElement) {
    HierarchyNodeDescriptor rootNodeDescriptor = (HierarchyNodeDescriptor)treeStructure.getRootElement();
    rootNodeDescriptor.update();
    if (rootElement == null || !NODE_ELEMENT_NAME.equals(rootElement.getName())) {
      throw new IllegalArgumentException("Incorrect root element in verification resource");
    }
    checkNodeDescriptorRecursively(treeStructure, rootNodeDescriptor, rootElement);
  }

  private static void checkNodeDescriptorRecursively(@NotNull HierarchyTreeStructure treeStructure,
                                                     @NotNull HierarchyNodeDescriptor descriptor,
                                                     @NotNull Element expectedElement) {
    checkBaseNode(treeStructure, descriptor, expectedElement);
    checkContent(descriptor, expectedElement);
    checkChildren(treeStructure, descriptor, expectedElement);
  }

  private static void checkBaseNode(@NotNull HierarchyTreeStructure treeStructure,
                                    @NotNull HierarchyNodeDescriptor descriptor,
                                    @NotNull Element expectedElement) {
    String baseAttrValue = expectedElement.getAttributeValue(BASE_ATTR_NAME);
    HierarchyNodeDescriptor baseDescriptor = treeStructure.getBaseDescriptor();
    boolean mustBeBase = "true".equalsIgnoreCase(baseAttrValue);
    assertEquals("Incorrect base node", mustBeBase, baseDescriptor == descriptor);
  }

  private static void checkContent(@NotNull HierarchyNodeDescriptor descriptor,
                                   @NotNull Element expectedElement) {
    assertEquals(expectedElement.getAttributeValue(TEXT_ATTR_NAME), descriptor.getHighlightedText().getText());
  }

  private static void checkChildren(@NotNull HierarchyTreeStructure treeStructure,
                                    @NotNull HierarchyNodeDescriptor descriptor,
                                    @NotNull Element element) {
    if (element.getChild(ANY_NODES_ELEMENT_NAME) != null) {
      return;
    }

    Object[] children = treeStructure.getChildElements(descriptor);
    List<Element> expectedChildren = new ArrayList<>(element.getChildren(NODE_ELEMENT_NAME));

    StringBuilder messageBuilder = new StringBuilder("Actual children of [" + descriptor.getHighlightedText().getText() + "]:\n");
    for (Object child : children) {
      HierarchyNodeDescriptor nodeDescriptor = (HierarchyNodeDescriptor)child;
      nodeDescriptor.update();
      messageBuilder.append("    [").append(nodeDescriptor.getHighlightedText().getText()).append("]\n");
    }
    assertEquals(messageBuilder.toString(), expectedChildren.size(), children.length);

    Arrays.sort(children, Comparator.comparing(child -> ((HierarchyNodeDescriptor)child).getHighlightedText().getText()));

    Collections.sort(expectedChildren, Comparator.comparing(child -> child.getAttributeValue(TEXT_ATTR_NAME)));

    Iterator<Element> iterator = expectedChildren.iterator();
    for (Object child : children) {
      checkNodeDescriptorRecursively(treeStructure, ((HierarchyNodeDescriptor)child), iterator.next());
    }
  }
}
