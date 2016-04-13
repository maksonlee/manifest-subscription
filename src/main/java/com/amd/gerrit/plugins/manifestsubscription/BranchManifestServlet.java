// Copyright (C) 2016 Advanced Micro Devices, Inc.  All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.amd.gerrit.plugins.manifestsubscription;

import com.google.gerrit.extensions.annotations.Export;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Export("/branch")
@Singleton
public class BranchManifestServlet extends HttpServlet {

  @Inject
  private GitRepositoryManager gitRepoManager;

  protected void doPost(HttpServletRequest req, HttpServletResponse res)
                                                            throws IOException {

    Map<String, String[]> input = req.getParameterMap();

    if (inputValid(req)) {
      res.setContentType("application/json");
      res.setCharacterEncoding("UTF-8");

      Utilities.branchManifest(gitRepoManager,
                                input.get("manifest-repo")[0],
                                input.get("manifest-commit-ish")[0],
                                input.get("manifest-path")[0],
                                input.get("new-branch")[0],
                                res.getWriter(), null, true);

    } else {
      res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }
  }

  private boolean inputValid(HttpServletRequest request) {
    Map<String, String[]> input = request.getParameterMap();
    if(input.containsKey("manifest-repo") &&
        input.containsKey("manifest-commit-ish") &&
        input.containsKey("manifest-path") &&
        input.containsKey("new-branch")) {
      return true;
    }

    return false;
  }
}
