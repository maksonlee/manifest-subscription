package com.amd.gerrit.plugins.manifestsubscription;

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.BranchResource;
import com.google.inject.Inject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BranchManifestLog implements RestReadView<BranchResource> {
    private String since;
    private String until;

    @Option(
            name = "--since",
            metaVar = "DATE",
            required = true
    )
    public void setSince(String since) {
        this.since = since;
    }

    @Option(
            name = "--until",
            metaVar = "DATE"
    )
    public void setUntil(String until) {
        this.until = until;
    }

    private final GitRepositoryManager gitManager;

    @Inject
    public BranchManifestLog(GitRepositoryManager gitManager) {
        this.gitManager = gitManager;
    }

    @Override
    public Object apply(BranchResource resource) throws IOException, BadRequestException, ResourceNotFoundException {
        Date dSince;
        Date dUntil;

        try {
            dSince = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(since);
            dUntil = until == null ? new Date() : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(until);
        } catch (ParseException e) {
            throw new BadRequestException(e.getMessage());
        }

        Repository repo = gitManager.openRepository(resource.getNameKey());
        Git git = new Git(repo);

        List<CommitInfo> result = new ArrayList();
        long minCommitTime = 0;

        try {
            Iterable<RevCommit> commits = git.log().add(repo.resolve(resource.getRef())).call();

            for (RevCommit commit : commits) {
                minCommitTime = commit.getCommitTime() * 1000L;

                if (commit.getCommitTime() * 1000L < dSince.getTime()) {
                    break;
                }

                if (commit.getCommitTime() * 1000L < dUntil.getTime() &&
                        commit.getFullMessage().split("\n").length >= 5) {
                    result.addAll(getCommits(commit));
                }
            }
        } catch (GitAPIException e) {
            throw new IOException();
        }

        if (dSince.getTime() < minCommitTime) {
            throw new BadRequestException("we only have data since " + new Date(minCommitTime));
        }

        return result;
    }

    private List<CommitInfo> getCommits(RevCommit commit) throws IOException, GitAPIException {
        List<CommitInfo> result = new ArrayList();

        String project = commit.getFullMessage().split("\n")[2];
        Repository repo = gitManager.openRepository(new Project.NameKey(project));
        RevWalk rw = new RevWalk(repo);
        commit = rw.parseCommit(ObjectId.fromString(commit.getFullMessage().split("\n")[3]));

        if (commit.getParentCount() == 2) {
            Git git = new Git(repo);
            Iterable<RevCommit> commits = git.log().addRange(commit.getParents()[0], commit).call();
            for (RevCommit c : commits) {
                result.add(new CommitInfo(project, c.getName(), c.getFullMessage()));
            }
        } else if (commit.getParentCount() == 1) {
            result.add(new CommitInfo(project, commit.getName(), commit.getFullMessage()));
        }

        return result;
    }

    private class CommitInfo {
        String project;
        String commit;
        String message;

        public CommitInfo(String project, String commit, String message) {
            this.project = project;
            this.commit = commit;
            this.message = message;
        }
    }
}
