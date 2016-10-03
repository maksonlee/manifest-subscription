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
import com.amd.gerrit.plugins.manifestsubscription.manifest.ManifestTest;
import org.apache.commons.compress.utils.IOUtils;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.*;
import org.junit.rules.ExpectedException;

import static com.google.common.truth.Truth.assertThat;

public class VersionedManifestsTest extends LocalDiskRepositoryTestCase {

  private Repository db;
  private TestRepository<Repository> util;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @BeforeClass
  public static void setUpBeforeClass() {

  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    db = createBareRepository();
    util = new TestRepository<>(db);
  }
  @Test
  public void testVersionedManifestsReadFromNonManifestGit() throws Exception {
    RevCommit rev = util.commit(util.tree(
        util.file("nonxml.txt",
            util.blob(IOUtils.toByteArray(
                getClass().getResourceAsStream("/nonxml.txt"))))
    ));

    VersionedManifests versionedManifests =
        new VersionedManifests("master");

    versionedManifests.load(db, rev);
  }

  @Test
  public void testVersionedManifestsReadFromGit() throws Exception {
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
        util.file("nonxml.txt",
            util.blob(IOUtils.toByteArray(
                getClass().getResourceAsStream("/nonxml.txt")))),
        util.file("testonly.xml",
            util.blob(IOUtils.toByteArray(
                getClass().getResourceAsStream("/testonly.xml"))))
    ));

    VersionedManifests versionedManifests =
        new VersionedManifests("master");

    versionedManifests.load(db, rev);

    Manifest manifest;

    manifest = versionedManifests.readManifest("aosp.xml");
    ManifestTest.checkAOSPcontent(manifest);

    manifest = versionedManifests.readManifest("aospinclude.xml");
    assertThat(manifest.getInclude()).isNotEmpty();
    manifest = versionedManifests.readManifest("aospincludereplace.xml");
    assertThat(manifest.getInclude()).isNotEmpty();
    manifest = versionedManifests.readManifest("multipleincludes.xml");
    assertThat(manifest.getInclude()).isNotEmpty();

    manifest = versionedManifests.readManifest("testonly.xml");
    ManifestTest.checkTestOnlyContent(manifest);

    manifest = versionedManifests.readManifest("subdir/aospincludereplace.xml");
    assertThat(manifest.getInclude()).isNotEmpty();
    assertThat(manifest.getInclude().get(0).getName()).isEqualTo("aospinclude.xml");
    assertThat(manifest.getInclude().get(1).getName()).isEqualTo("subdir/testonly1.xml");

    thrown.expect(ManifestReadException.class);
    manifest = versionedManifests.readManifest("nonxml.txt");
  }

  @After
  public void tearDown() throws Exception {

  }
}