// Copyright 2018 The Closure Stylesheets Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.common.css.compiler.passes;

import com.google.common.css.JobDescription;
import com.google.common.css.compiler.ast.CssTree;
import com.google.common.css.compiler.ast.ErrorManager;

/**
 * A pass that collects all mixin definitions and afterwards replaces them with
 * the corresponding definitions.
 */
public class CollectAndReplaceMixins implements NewCssCompilerPass {

  public CollectAndReplaceMixins() {}

  @Override
  public boolean run(JobDescription job, CssTree cssTree, ErrorManager errorManager) {
    CollectMixinDefinitions collectMixinDefinitions =
        new CollectMixinDefinitions(
            cssTree.getMutatingVisitController(), errorManager);
    collectMixinDefinitions.runPass();
    new ReplaceMixins(
        cssTree.getMutatingVisitController(), errorManager,
        collectMixinDefinitions.getDefinitions()).runPass();
    return true;
  }
}
