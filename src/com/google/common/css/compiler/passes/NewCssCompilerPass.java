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
import com.google.common.css.compiler.ast.CssCompilerPass;
import com.google.common.css.compiler.ast.CssTree;
import com.google.common.css.compiler.ast.ErrorManager;

/**
 */
public interface NewCssCompilerPass {
  /**
   * Executes this compiler pass.
   *
   * @param job ...
   * @param cssTree The CSS tree to run this pass on.
   * @param errorManager The error manager to report errors to.
   * @return Whether the execution was successful.
   */
  boolean run(JobDescription job, CssTree cssTree, ErrorManager errorManager);


  public class WrapLegacyCssCompilerPass implements NewCssCompilerPass {
    private final CssCompilerPass pass;

    public WrapLegacyCssCompilerPass(CssCompilerPass pass) {
      this.pass = pass;
    }

    @Override
    public boolean run(JobDescription job, CssTree cssTree, ErrorManager errorManager) {
      this.pass.runPass();
      return true;
    }
  }
}
