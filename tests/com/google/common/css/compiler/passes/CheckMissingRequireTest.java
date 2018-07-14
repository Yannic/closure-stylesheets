/*
 * Copyright 2013 Google Inc.
 *
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

package com.google.common.css.compiler.passes;

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.css.JobDescription;
import com.google.common.css.JobDescriptionBuilder;
import com.google.common.css.compiler.ast.GssParserException;
import com.google.common.css.compiler.ast.testing.NewFunctionalTestBase;
import com.google.common.css.compiler.passes.NewCssCompilerPass.WrapLegacyCssCompilerPass;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link CheckMissingRequire}.
 *
 */
@RunWith(JUnit4.class)
public class CheckMissingRequireTest extends NewFunctionalTestBase {

  protected void runPasses(TestErrorManager errorMgr) {
    List<NewCssCompilerPass> l = Lists.newArrayList();
    l.add(new WrapLegacyCssCompilerPass(
          new CreateMixins(tree.getMutatingVisitController(), errorMgr)));
    l.add(new WrapLegacyCssCompilerPass(
          new CreateDefinitionNodes(tree.getMutatingVisitController(), errorMgr)));
    l.add(new WrapLegacyCssCompilerPass(
          new CreateConstantReferences(tree.getMutatingVisitController())));
    l.add(new CheckDependencyNodes());
    l.add(new WrapLegacyCssCompilerPass(
          new CreateComponentNodes(tree.getMutatingVisitController(), errorMgr)));
    l.add(new WrapLegacyCssCompilerPass(
          new CollectMixinDefinitions(tree.getMutatingVisitController(), errorMgr)));
    l.add(new WrapLegacyCssCompilerPass(
          new ProcessComponents<Object>(tree.getMutatingVisitController(), errorMgr, null)));

    CollectProvideNamespaces collectProvides = new CollectProvideNamespaces(
        tree.getVisitController());
    l.add(new WrapLegacyCssCompilerPass(collectProvides));
    l.add(new WrapLegacyCssCompilerPass(new CheckMissingRequire(
        tree.getVisitController(),
        errorMgr,
        collectProvides.getFilenameProvideMap(),
        collectProvides.getFilenameRequireMap(),
        collectProvides.getDefProvideMap(),
        collectProvides.getDefmixinProvideMap())));

    JobDescriptionBuilder jobBuilder = new JobDescriptionBuilder();
    jobBuilder.setSuppressDependencyCheck(true);
    JobDescription job = jobBuilder.getJobDescription();
    for (NewCssCompilerPass pass : l) {
      pass.run(job, tree, errorMgr);
    }
  }

  @Test
  public void testBaseCase1() throws GssParserException {
    String base =  ""
        + "@provide 'foo.base';"
        + "@def FOO_BASE_COLOR     #fff;";
    String streamitem =  ""
        + "@provide 'foo.streamitem';"
        + "@require 'foo.base';"
        + ".nav {"
        + "  color: FOO_BASE_COLOR;"
        + "}";
    String streamcomponent = ""
        + "@provide 'foo.streamcomponent';"
        + "@require 'foo.base';"
        + "@component {"
        + "  .nav {"
        + "    color: FOO_BASE_COLOR;"
        + "  }"
        + "}";
    ImmutableMap<String, String> fileNameToGss = ImmutableMap.of(
        "base.gss", base,
        "streamitem.gss", streamitem,
        "streamcomponent.gss", streamcomponent);

    parseAndBuildTree(fileNameToGss);
    String[] expectedMessages = {};
    TestErrorManager errorManager = new TestErrorManager(false, expectedMessages);
    runPasses(errorManager);
    errorManager.generateReport();
    assertWithMessage("Encountered all errors.")
        .that(errorManager.hasEncounteredAllErrors())
        .isTrue();
  }

  @Test
  public void testBaseCase2() throws GssParserException {
    String base =  ""
        + "@provide 'foo.base';"
        + "@def FOO_BASE_COLOR     #fff;"
        + "@def FOO_BASE_BG_COLOR  FOO_BASE_COLOR;";
    ImmutableMap<String, String> fileNameToGss = ImmutableMap.of("base.gss", base);

    parseAndBuildTree(fileNameToGss);
    String[] expectedMessages = {};
    TestErrorManager errorManager = new TestErrorManager(false, expectedMessages);
    runPasses(errorManager);
    errorManager.generateReport();
    assertWithMessage("Encountered all errors.")
        .that(errorManager.hasEncounteredAllErrors())
        .isTrue();
  }

  @Test
  public void testMissingRequire() throws GssParserException {
    String base =  ""
        + "@provide 'foo.base';"
        + "@def FOO_BASE_COLOR     #fff;";
    String streamitem =  ""
        + "@provide 'foo.streamitem';"
        + ".nav {"
        + "  color: FOO_BASE_COLOR;"
        + "}";
    ImmutableMap<String, String> fileNameToGss = ImmutableMap.of(
        "base.gss", base,
        "streamitem.gss", streamitem);

    parseAndBuildTree(fileNameToGss);
    String[] expectedMessages = {"Missing @require for constant FOO_BASE_COLOR."};
    TestErrorManager errorManager = new TestErrorManager(false, expectedMessages);
    runPasses(errorManager);
    errorManager.generateReport();
    assertWithMessage("Encountered all errors.")
        .that(errorManager.hasEncounteredAllErrors())
        .isTrue();
  }

  @Test
  public void testMissingRequire_overrideSelector() throws GssParserException {
    String base = ""
        + "@provide 'foo.base';\n"
        + ".nav { color: blue; }\n";
    String streamitem = ""
        + "@provide 'foo.streamitem';\n"
        + "/** @overrideSelector {foo.base} */\n"
        + ".nav { color: red; }\n";
    ImmutableMap<String, String> fileNameToGss =
        ImmutableMap.of(
            "base.gss", base,
            "streamitem.gss", streamitem);

    parseAndBuildTree(fileNameToGss);
    String[] expectedMessages = {
      "Missing @require for @overrideSelector {foo.base}. Please @require this namespace in "
          + "file: streamitem.gss."
    };
    TestErrorManager errorManager = new TestErrorManager(false, expectedMessages);
    runPasses(errorManager);
    errorManager.generateReport();
    assertWithMessage("Encountered all errors.")
        .that(errorManager.hasEncounteredAllErrors())
        .isTrue();
  }

  @Test
  public void testMissingRequireFromComponent() throws GssParserException {
    String base =  ""
        + "@provide 'foo.base';"
        + "@def FOO_BASE_COLOR     #fff;";
    String streamitem =  ""
        + "@provide 'foo.streamitem';"
        + "@component {"
        + "  @def NAV_COLOR  FOO_BASE_COLOR;"
        + "  .nav {"
        + "    color: NAV_COLOR;"
        + "  }"
        + "}";
    ImmutableMap<String, String> fileNameToGss = ImmutableMap.of(
        "base.gss", base,
        "streamitem.gss", streamitem);

    parseAndBuildTree(fileNameToGss);
    String[] expectedMessages = {"Missing @require for constant FOO_BASE_COLOR."};
    TestErrorManager errorManager = new TestErrorManager(false, expectedMessages);
    runPasses(errorManager);
    errorManager.generateReport();
    assertWithMessage("Encountered all errors.")
        .that(errorManager.hasEncounteredAllErrors())
        .isTrue();
  }

  @Test
  public void testMissingRequireOfComponent() throws GssParserException {
    String basecomponent =  ""
        + "@provide 'foo.basecomponent';"
        + "@component {"
        + "  @def COLOR     #fff;"
        + "}";
    String streamitem =  ""
        + "@provide 'foo.streamitem';"
        + ".nav {"
        + "  color: FOO_BASECOMPONENT_COLOR;"
        + "}";
    ImmutableMap<String, String> fileNameToGss = ImmutableMap.of(
        "basecomponent.gss", basecomponent,
        "streamitem.gss", streamitem);

    parseAndBuildTree(fileNameToGss);
    String[] expectedMessages = {"Missing @require for constant FOO_BASECOMPONENT_COLOR."};
    TestErrorManager errorManager = new TestErrorManager(false, expectedMessages);
    runPasses(errorManager);
    errorManager.generateReport();
    assertWithMessage("Encountered all errors.")
        .that(errorManager.hasEncounteredAllErrors())
        .isTrue();
  }

  @Test
  public void testMissingRequireInDef() throws GssParserException {
    String base =  ""
        + "@provide 'foo.base';"
        + "@def FOO_BASE_COLOR     #fff;";
    String streamitem =  ""
        + "@provide 'foo.streamitem';"
        + "@def FOO_STREAM_ITEM_COLOR  FOO_BASE_COLOR;"
        + "@def FOO_BASE_FONT_SIZE     10px;";
    ImmutableMap<String, String> fileNameToGss = ImmutableMap.of(
        "base.gss", base,
        "streamitem.gss", streamitem);

    parseAndBuildTree(fileNameToGss);
    String[] expectedMessages = {"Missing @require for constant FOO_BASE_COLOR."};
    TestErrorManager errorManager = new TestErrorManager(false, expectedMessages);
    runPasses(errorManager);
    errorManager.generateReport();
    assertWithMessage("Encountered all errors.")
        .that(errorManager.hasEncounteredAllErrors())
        .isTrue();
  }

  @Test
  public void testMissingRequireDefMixin() throws GssParserException {
    String base =  ""
        + "@provide 'foo.base';\n"
        + "/**\n"
        + " * @param FALLBACK_BG_COLOR The background color to use.\n"
        + " */\n"
        + "@defmixin background_color(FALLBACK_BG_COLOR) {\n"
        + "  background-color: FALLBACK_BG_COLOR;\n"
        + "}\n";
    String streamitem =  ""
        + "@provide 'foo.streamitem';\n"
        + "@def FOO_OVERLAY_BG_COLOR  #fff;\n"
        + ".fooStreamOverlay {\n"
        + "@mixin background_color(FOO_OVERLAY_BG_COLOR);\n"
        + "}\n";

    ImmutableMap<String, String> fileNameToGss = ImmutableMap.of(
        "base.gss", base,
        "streamitem.gss", streamitem);

    parseAndBuildTree(fileNameToGss);
    String[] expectedMessages = {"Missing @require for mixin background_color."};
    TestErrorManager errorManager = new TestErrorManager(false, expectedMessages);
    runPasses(errorManager);
    errorManager.generateReport();
    assertWithMessage("Encountered all errors.")
        .that(errorManager.hasEncounteredAllErrors())
        .isTrue();
  }

  @Test
  public void testMissingOverrideSelectorNamespace() throws GssParserException {
    String base =  ""
        + "@provide 'foo.base';"
        + "@def FOO_BASE_COLOR     #fff;";
    String streamitem =  ""
        + "@provide 'foo.streamitem';"
        + "@require 'foo.base';"
        + "/* @overrideSelector {foo.foo} */ .nav {"
        + "  color: FOO_BASE_COLOR;"
        + "}";

    ImmutableMap<String, String> fileNameToGss = ImmutableMap.of(
        "base.gss", base,
        "streamitem.gss", streamitem);
    parseAndBuildTree(fileNameToGss);
    String[] expectedMessages = {"Missing @require for @overrideSelector"};
    TestErrorManager errorManager = new TestErrorManager(false, expectedMessages);
    runPasses(errorManager);
    errorManager.generateReport();
    assertWithMessage("Encountered all errors.")
        .that(errorManager.hasEncounteredAllErrors())
        .isTrue();
  }

  @Test
  public void testMissingOverrideSelectorNamespace_multilineComment() throws GssParserException {
    String base =  ""
        + "@provide 'foo.base';\n"
        + "@def FOO_BASE_COLOR     #fff;\n";
    String streamitem =  ""
        + "@provide 'foo.streamitem';\n"
        + "@require 'foo.base';\n"
        + "/**\n"
        + " * @overrideSelector {foo.foo}\n"
        + " */\n"
        + ".nav {\n"
        + "  color: FOO_BASE_COLOR;\n"
        + "}\n";

    ImmutableMap<String, String> fileNameToGss = ImmutableMap.of(
        "base.gss", base,
        "streamitem.gss", streamitem);
    parseAndBuildTree(fileNameToGss);
    String[] expectedMessages = {"Missing @require for @overrideSelector"};
    TestErrorManager errorManager = new TestErrorManager(false, expectedMessages);
    runPasses(errorManager);
    errorManager.generateReport();
    assertWithMessage("Encountered all errors.")
        .that(errorManager.hasEncounteredAllErrors())
        .isTrue();
  }

  @Test
  public void testMissingOverrideDefNamespace() throws GssParserException {
    String base =  ""
        + "@provide 'foo.base';"
        + "@def FOO_BASE_COLOR     #fff;";
    String streamitem =  ""
        + "@provide 'foo.streamitem';"
        + "@require 'foo.bar';"
        + "/* @overrideDef {foo.base} */ @def FOO_BASE_COLOR  #ffe;";

    ImmutableMap<String, String> fileNameToGss = ImmutableMap.of(
        "base.gss", base,
        "streamitem.gss", streamitem);
    parseAndBuildTree(fileNameToGss);
    String[] expectedMessages = {"Missing @require for @overrideDef"};
    TestErrorManager errorManager = new TestErrorManager(false, expectedMessages);
    runPasses(errorManager);
    errorManager.generateReport();
    assertWithMessage("Encountered all errors.")
        .that(errorManager.hasEncounteredAllErrors())
        .isTrue();
  }

  @Test
  public void testMissingOverrideDefNamespace_multilineComment() throws GssParserException {
    String base =  ""
        + "@provide 'foo.base';\n"
        + "@def FOO_BASE_COLOR     #fff;\n";
    String streamitem =  ""
        + "@provide 'foo.streamitem';\n"
        + "@require 'foo.bar';\n"
        + "/**\n"
        + " * @overrideDef {foo.base}\n"
        + " */\n"
        + "@def FOO_BASE_COLOR  #ffe;\n";

    ImmutableMap<String, String> fileNameToGss = ImmutableMap.of(
        "base.gss", base,
        "streamitem.gss", streamitem);
    parseAndBuildTree(fileNameToGss);
    String[] expectedMessages = {"Missing @require for @overrideDef"};
    TestErrorManager errorManager = new TestErrorManager(false, expectedMessages);
    runPasses(errorManager);
    errorManager.generateReport();
    assertWithMessage("Encountered all errors.")
        .that(errorManager.hasEncounteredAllErrors())
        .isTrue();
  }
}
