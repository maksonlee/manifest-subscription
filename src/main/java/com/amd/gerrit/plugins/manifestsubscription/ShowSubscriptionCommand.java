// Copyright (C) 2015 Advanced Micro Devices, Inc. All rights reserved.
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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import org.kohsuke.args4j.Option;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "show", description = "Show states of manifest-subscription")
public class ShowSubscriptionCommand extends SshCommand {

  @Inject
  private ManifestSubscription manifestSubscription;

  @Option(name = "-o", aliases = {"--output-type"},
      usage = "", required = false)
  private Utilities.OutputType outputType;

  @Override
  protected void run() {
    Utilities.showSubscription(manifestSubscription, stdout,
        outputType==Utilities.OutputType.JSON);
  }
}
