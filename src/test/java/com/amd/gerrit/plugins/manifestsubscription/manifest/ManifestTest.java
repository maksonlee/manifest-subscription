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

package com.amd.gerrit.plugins.manifestsubscription.manifest;

import com.amd.gerrit.plugins.manifestsubscription.ManifestProvider;
import com.amd.gerrit.plugins.manifestsubscription.ManifestReadException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.net.URL;

import static com.google.common.truth.Truth.assertThat;

public class ManifestTest {
  private static Unmarshaller unmarshaller;
  private static ManifestProvider provider;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @BeforeClass
  public static void setUpBeforeClass() throws JAXBException {
    JAXBContext jaxbctx = JAXBContext.newInstance(Manifest.class);
    unmarshaller = jaxbctx.createUnmarshaller();
    provider = new ManifestProvider() {
      @Override
      public Manifest readManifest(String path) throws JAXBException, ManifestReadException {
        URL url = getClass().getResource("/" + path);
        File manifestFile = new File(url.getFile());

        return (Manifest) unmarshaller.unmarshal(manifestFile);
      }
    };
  }

  @Before
  public void setUp() {

  }

  @Test
  public void testBadManifest() throws JAXBException, ManifestReadException {
    thrown.expect(JAXBException.class);
    provider.readManifest("bad.xml");
  }

  @Test
  public void testManifests() throws JAXBException, ManifestReadException {
    Manifest manifest;

    manifest = provider.readManifest("aospinclude.xml");
    assertThat(manifest.getInclude()).isNotEmpty();
    manifest = provider.readManifest("aospincludereplace.xml");
    assertThat(manifest.getInclude()).isNotEmpty();
    manifest = provider.readManifest("multipleincludes.xml");
    assertThat(manifest.getInclude()).isNotEmpty();
    assertThat(manifest.getDefault()).isNull();
    manifest = provider.readManifest("testonly.xml");
    assertThat(manifest.getInclude()).isEmpty();
    checkTestOnlyContent(manifest);
    manifest = provider.readManifest("subdir/aospincludereplace.xml");
    assertThat(manifest.getInclude()).isNotEmpty();
  }

  @Test
  public void testAOSPManifest() throws JAXBException, ManifestReadException {
    Manifest manifest = provider.readManifest("aosp.xml");

    // asserts some of the properties from reading aosp.xml manually
    assertThat(manifest.getRemote()).hasSize(1);

    assertThat(manifest.getProject()).hasSize(515);

    checkAOSPcontent(manifest);
  }

  public static void checkAOSPcontent(Manifest manifest) {
    assertThat(manifest.getInclude()).isEmpty();
    assertThat(manifest.getRemote()).isNotEmpty();
    assertThat(manifest.getDefault()).isNotNull();

    assertThat(manifest.getDefault().getRevision()).isEqualTo("master");
    assertThat(((Remote) manifest.getDefault().getRemote()).getName()).isEqualTo("aosp");
    assertThat(manifest.getDefault().getSyncJ()).isEqualTo("4");

    for (Project p : manifest.getProject()) {
      switch (p.getPath()) {
        case "build":
          assertThat(p.getCopyfile()).hasSize(1);
          assertThat(p.getCopyfile().get(0).getDest()).isEqualTo("Makefile");
          assertThat(p.getGroups()).isEqualTo("pdk,tradefed");
          break;
        case "build/soong":
          assertThat(p.getCopyfile()).isEmpty();
          assertThat(p.getLinkfile()).hasSize(2);
          break;
        case "external/curl":
          assertThat(p.getGroups()).isNull();
          break;
        case "tools/tradefederation":
          assertThat(p.getGroups()).isEqualTo("notdefault,tradefed");
          assertThat(p.getName()).isEqualTo("platform/tools/tradefederation");
          break;
      }
    }

  }

  public static void checkTestOnlyContent(Manifest manifest) {
    assertThat(manifest.getInclude()).isEmpty();

    for (Project p : manifest.getProject()) {
      switch (p.getPath()) {
        case "git/test/project1":
          assertThat(p.getGroups()).isEqualTo("demo");
          assertThat(p.getRevision()).isEqualTo("master");
          break;
        case "git/test/project2":
          assertThat(p.getGroups()).isNull();
          break;
        case "git/test/project3":
          assertThat(p.getRevision()).isEqualTo("master");
          break;
      }
    }

  }
}
