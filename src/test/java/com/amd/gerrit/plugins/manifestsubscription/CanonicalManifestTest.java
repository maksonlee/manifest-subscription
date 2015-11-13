// Copyright (C) 2015 Advanced Micro Devices, Inc.  All rights reserved.
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

import com.amd.gerrit.plugins.manifestsubscription.manifest.Manifest;
import com.amd.gerrit.plugins.manifestsubscription.manifest.Project;
import org.apache.commons.compress.utils.IOUtils;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.amd.gerrit.plugins.manifestsubscription.manifest.ManifestTest.checkAOSPcontent;
import static com.amd.gerrit.plugins.manifestsubscription.manifest.ManifestTest.checkTestOnlyContent;
import static com.google.common.truth.Truth.assertThat;

public class CanonicalManifestTest extends LocalDiskRepositoryTestCase{
  private Repository db;
  private TestRepository<Repository> util;
  private VersionedManifests versionedManifests;
  private CanonicalManifest canonicalManifest;
  private Manifest manifest;
  private ManifestProvider provider;


  @BeforeClass
  public static void setUpBeforeClass() {

  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    db = createBareRepository();
    util = new TestRepository<>(db);

    RevCommit rev = util.commit(util.tree(
        util.file("aosp.xml",
            util.blob(IOUtils.toByteArray(
                getClass().getResourceAsStream("/aosp.xml")))),
        util.file("aospinclude.xml",
            util.blob(IOUtils.toByteArray(
                getClass().getResourceAsStream("/aospinclude.xml")))),
        util.file("aospincludereplace.xml",
            util.blob(IOUtils.toByteArray(
                getClass().getResourceAsStream("/aospincludereplace.xml")))),
        util.file("multipleincludes.xml",
            util.blob(IOUtils.toByteArray(
                getClass().getResourceAsStream("/multipleincludes.xml")))),
        util.file("subdir/aospincludereplace.xml",
            util.blob(IOUtils.toByteArray(
                getClass().getResourceAsStream("/subdir/aospincludereplace.xml")))),
        util.file("subdir/testonly1.xml",
            util.blob(IOUtils.toByteArray(
                getClass().getResourceAsStream("/subdir/testonly1.xml")))),
        util.file("testonly.xml",
            util.blob(IOUtils.toByteArray(
                getClass().getResourceAsStream("/testonly.xml"))))
    ));

    versionedManifests = new VersionedManifests(
        "master");

    versionedManifests.load(db, rev);
    canonicalManifest = new CanonicalManifest(versionedManifests);
  }

  @Test
  public void testCanonicalManifestsAOSPInclude() throws Exception {
    manifest = canonicalManifest.getCanonicalManifest("aospinclude.xml");
    checkAOSPcontent(manifest);
    checkTestOnlyContent(manifest);
  }

  @Test
  public void testCanonicalManifestsAOSPIncludeReplace() throws Exception {
    manifest = canonicalManifest.getCanonicalManifest("aospincludereplace.xml");
    checkAOSPcontent(manifest);

    for (Project p : manifest.getProject()) {
      switch (p.getPath()) {
        case "git/test/project1":
          assertThat(p.getGroups()).isNull();
          assertThat(p.getRevision()).isEqualTo("replaced");
          break;
        case "git/test/project2":
          assertThat(p.getGroups()).isNull();
          assertThat(p.getRevision()).isEqualTo("replaced");
          break;
        case "git/test/project3":
          assertThat(p.getGroups()).isNull();
          assertThat(p.getRevision()).isEqualTo("replaced");
          break;
      }
    }
  }

  @Test
  public void testCanonicalManifestsMultipleIncludes() throws Exception {
    manifest = canonicalManifest.getCanonicalManifest("multipleincludes.xml");
    checkAOSPcontent(manifest);
    checkTestOnlyContent(manifest);
  }

  @Test
  public void testCanonicalManifestsTestOnly() throws Exception {
    manifest = canonicalManifest.getCanonicalManifest("testonly.xml");
    assertThat(manifest.getInclude()).isEmpty();
    checkTestOnlyContent(manifest);
  }

  @Test
  public void testCanonicalManifestsSubdir() throws Exception {
    manifest = canonicalManifest.getCanonicalManifest("subdir/aospincludereplace.xml");
    checkAOSPcontent(manifest);
    checkTestOnlyContent(manifest);

    for (Project p : manifest.getProject()) {
      switch (p.getPath()) {
        case "git/test/project4":
          assertThat(p.getGroups()).isNull();
          assertThat(p.getRevision()).isEqualTo("replaced");
          break;
        case "git/test/project5":
          assertThat(p.getGroups()).isNull();
          assertThat(p.getRevision()).isEqualTo("replaced");
          break;
        case "git/test/project6":
          assertThat(p.getGroups()).isNull();
          assertThat(p.getRevision()).isEqualTo("replaced");
          break;
      }
    }
  }
}
