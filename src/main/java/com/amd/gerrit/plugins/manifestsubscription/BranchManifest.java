package com.amd.gerrit.plugins.manifestsubscription;

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.BranchResource;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BranchManifest implements RestReadView<BranchResource> {
    private String at;

    @Option(
            name = "--at",
            metaVar = "DATE"
    )
    public void setAt(String at) {
        this.at = at;
    }

    private final GitRepositoryManager gitManager;

    @Inject
    public BranchManifest(GitRepositoryManager gitManager) {
        this.gitManager = gitManager;
    }

    @Override
    public Object apply(BranchResource resource) throws IOException, BadRequestException, ResourceNotFoundException {
        Date dAt;
        try {
            dAt = at == null ? new Date() : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(at);
        } catch (ParseException e) {
            throw new BadRequestException(e.getMessage());
        }

        Repository repo = gitManager.openRepository(resource.getNameKey());
        Git git = new Git(repo);

        String result = null;
        long minCommitTime = 0;

        try {
            Iterable<RevCommit> commits = git.log().add(repo.resolve(resource.getRef())).call();

            for(RevCommit commit : commits) {
                minCommitTime = commit.getCommitTime() * 1000L;

                if (commit.getCommitTime() * 1000L < dAt.getTime()) {
                    result = commit.getName();
                    break;
                }
            }
        } catch (GitAPIException e) {
            throw new IOException();
        }

        if (dAt.getTime() < minCommitTime) {
            throw new BadRequestException("we only have data since " + new Date(minCommitTime));
        }

        return result;
    }
}
