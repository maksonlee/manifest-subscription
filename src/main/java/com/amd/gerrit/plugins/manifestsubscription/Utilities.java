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
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.amd.gerrit.plugins.manifestsubscription.manifest.Manifest;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBException;

public class Utilities {
  private static Gson gson = new GsonBuilder()
      .generateNonExecutableJson()
      .setPrettyPrinting()
      .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
      .create();

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
                             String manifestRepo, String manifestCommitish,
                             String manifestPath, String newBranch,
                             Writer output, PrintWriter error, boolean inJSON) {

    Manifest manifest;
    try {
      manifest = getManifest(gitRepoManager, manifestRepo,
                              manifestCommitish, manifestPath);
      VersionedManifests.branchManifest(gitRepoManager, manifest, newBranch);

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
