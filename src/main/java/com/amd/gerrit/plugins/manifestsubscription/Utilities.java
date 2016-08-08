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

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.amd.gerrit.plugins.manifestsubscription.manifest.Default;
import com.amd.gerrit.plugins.manifestsubscription.manifest.Manifest;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.JAXBException;

public class Utilities {
  private static final Logger log =
      LoggerFactory.getLogger(Utilities.class);
  private static Gson gson = new GsonBuilder()
      .generateNonExecutableJson()
      .setPrettyPrinting()
      .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
      .create();

  static boolean httpInputValid(HttpServletRequest request) {
    Map<String, String[]> input = request.getParameterMap();
    if (input.containsKey("manifest-repo") &&
        input.containsKey("manifest-commit-ish") &&
        input.containsKey("manifest-path") &&
        (input.containsKey("new-branch") || input.containsKey("new-tag")) ) {

      // these inputs are related.  Either they are all present or all absent
      if ((input.containsKey("new-manifest-repo") &&
          input.containsKey("new-manifest-branch") &&
          input.containsKey("new-manifest-path")) ||
          (!input.containsKey("new-manifest-repo") &&
              !input.containsKey("new-manifest-branch") &&
              !input.containsKey("new-manifest-path"))) {

        return true;
      }
    }

    return false;
  }

  public enum OutputType {
    TEXT,
    JSON
  }

  static Manifest getManifest(GitRepositoryManager gitRepoManager,
                             String manifestRepo, String manifestCommitish,
                             String manifestPath)
      throws ManifestReadException, IOException, ConfigInvalidException,
                                                                 JAXBException {

    Project.NameKey p = new Project.NameKey(manifestRepo);
    Repository repo = gitRepoManager.openRepository(p);
    ObjectId commitId = repo.resolve(manifestCommitish);
    VersionedManifests vManifests = new VersionedManifests(manifestCommitish);
    vManifests.load(repo, commitId);
    CanonicalManifest manifests = new CanonicalManifest(vManifests);

    return manifests.getCanonicalManifest(manifestPath);
  }

  static Manifest createNewManifestFromBase
                                    (GitRepositoryManager gitRepoManager,
                                     MetaDataUpdate.Server metaDataUpdateFactory,
                                     String manifestRepo,
                                     String manifestBranch,
                                     String manifestPath,
                                     String newRef,
                                     Manifest base)
      throws JAXBException, IOException, ConfigInvalidException, GitAPIException {

    // Replace default ref with newly created branch or tag
    Manifest manifest = (Manifest) base.clone();
    final String defaultRef;
    if (manifest.getDefault() != null) {
      defaultRef = manifest.getDefault().getRevision();
    } else {
      defaultRef = null;
    }

    if (manifest.getDefault() != null) {
      ManifestOp op = new ManifestOp() {
        @Override
        public boolean apply(com.amd.gerrit.plugins.manifestsubscription.manifest.Project project,
                             String hash, String name,
                             GitRepositoryManager gitRepoManager) throws
            GitAPIException, IOException {

          //This is assuming newRef points to the existing hash
          if (project.getRevision() != null && project.getRevision().equals(hash)) {
            project.setRevision(null);
          } else {
            project.setRevision(defaultRef);
          }

          return true;
        }
      };

      VersionedManifests.traverseManifestAndApplyOp(gitRepoManager,
          manifest.getProject(), defaultRef, op, null);
    } else {
      manifest.setDefault(new Default());
    }

    manifest.getDefault().setRevision(newRef);

    Project.NameKey p = new Project.NameKey(manifestRepo);
    Repository repo = gitRepoManager.openRepository(p);
    ObjectId commitId = repo.resolve(manifestBranch);
    VersionedManifests vManifests;
    MetaDataUpdate update = metaDataUpdateFactory.create(p);
    if (commitId == null) {
      // TODO remove assumption that master branch always exists
      vManifests = new VersionedManifests("refs/heads/master");
      vManifests.load(update);
    } else {
      vManifests = new VersionedManifests(manifestBranch);
      vManifests.load(repo, commitId);
    }


    Map<String, Manifest> entry = Maps.newHashMapWithExpectedSize(1);
    entry.put(manifestPath, manifest);
    vManifests.setManifests(entry);

    RevCommit commit;
    if (commitId == null) {
      commit = vManifests.commitToNewRef(update, manifestBranch);
    } else {
      commit = vManifests.commit(update);
    }

    //TODO
    //if (commit != null) {
    //  changeHooks.doRefUpdatedHook(new Branch.NameKey(p, refName),
    //      commit.getParent(0).getId(),
    //      commit.getId(), null);
    //} else {
    //  log.warn("Failing to create new manifest");
    //}
    return manifest;
  }

  private static void outputError(Writer output, PrintWriter error,
                                  boolean inJSON, Exception e) {
    if (inJSON) {
      Map<String, Map<String, String>> errorJSON = Maps.newHashMap();
      errorJSON.put("error", Maps.<String, String>newHashMap());
      errorJSON.get("error").put("message",
          Throwables.getStackTraceAsString(e));

      gson.toJson(errorJSON, output);
    } else {
      e.printStackTrace(error);
    }
  }

  private static void outputSuccess(String type, String newRef, Writer output,
                                    boolean inJSON, Manifest manifest) {
    if (inJSON) {
      gson.toJson(manifest, output);
    } else {
      PrintWriter stdout;
      if (output instanceof PrintWriter) {
        stdout = (PrintWriter) output;
      } else {
        stdout = new PrintWriter(output);
      }

      stdout.println("");
      stdout.println(type + " '" + newRef +
          "' will be created for the following projects:");
      for (com.amd.gerrit.plugins.manifestsubscription.manifest.Project proj :
          manifest.getProject()) {
        stdout.print(proj.getRevision());
        stdout.print("\t");
        stdout.println(proj.getName());
      }
    }
  }

  static void branchManifest(GitRepositoryManager gitRepoManager,
                             MetaDataUpdate.Server metaDataUpdateFactory,
                             String manifestRepo, String manifestCommitish,
                             String manifestPath, String newBranch,
                             String newManifestRepo,
                             String newManifestBranch,
                             String newManifestPath,
                             Writer output, PrintWriter error, boolean inJSON) {

    Manifest manifest;
    try {
      manifest = getManifest(gitRepoManager, manifestRepo,
                              manifestCommitish, manifestPath);
      VersionedManifests.branchManifest(gitRepoManager, manifest, newBranch);

      if (newManifestBranch != null &&
          newManifestPath != null &&
          newManifestRepo != null) {
        createNewManifestFromBase(gitRepoManager, metaDataUpdateFactory,
            newManifestRepo, newManifestBranch, newManifestPath,
            newBranch, manifest);
      }

    } catch (IOException | ConfigInvalidException | ManifestReadException |
        JAXBException | GitAPIException e) {
      outputError(output, error, inJSON, e);
      return;
    }

    outputSuccess("Branch", newBranch, output, inJSON, manifest);
  }

  static void tagManifest(GitRepositoryManager gitRepoManager,
                             String manifestRepo, String manifestCommitish,
                             String manifestPath, String newTag,
                             Writer output, PrintWriter error, boolean inJSON) {

    Manifest manifest;
    try {
      manifest = getManifest(gitRepoManager, manifestRepo,
          manifestCommitish, manifestPath);
      VersionedManifests.tagManifest(gitRepoManager, manifest, newTag);

    } catch (IOException | ConfigInvalidException | ManifestReadException |
        JAXBException | GitAPIException e) {
      outputError(output, error, inJSON, e);
      return;
    }

    outputSuccess("Tag", newTag, output, inJSON, manifest);
  }

  static void showSubscription(ManifestSubscription manifestSubscription,
                               Writer output, boolean inJSON) {

    Set<String> repos = manifestSubscription.getEnabledManifestSource();
    Set<ProjectBranchKey> projects = manifestSubscription.getSubscribedProjects();
    

    if (inJSON) {

      Map<String, Set> result = Maps.newHashMap();

      result.put("manifest_subscriptions", repos);
      result.put("monitored_projects", projects);

      gson.toJson(result, output);

    } else {
      PrintWriter writer;
      if (output instanceof PrintWriter) {
        writer = (PrintWriter) output;
      } else {
        writer = new PrintWriter(output);
      }

      writer.println("Enabled manifest repositories:");

      for (String repo : repos) {
        writer.println(repo);
      }

      writer.println("");
      writer.println("Monitoring projects:");

      for (ProjectBranchKey project : projects) {
        writer.println(project.getProject() + " | " + project.getBranch());
      }

    }
  }
}
