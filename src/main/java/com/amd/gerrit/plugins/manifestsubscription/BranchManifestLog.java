package com.amd.gerrit.plugins.manifestsubscription;

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.BranchResource;
import com.google.inject.Inject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.TreeRevFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;
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
    private boolean noMerges;

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

    @Option(
            name = "--no-merges",
            metaVar = "BOOLEAN"
    )
    public void setNoMerges(boolean noMerges) {
        this.noMerges = noMerges;
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
            System.out.println(noMerges);
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

        Date submitDate = commit.getCommitterIdent().getWhen();
        String project = commit.getFullMessage().split("\n")[2];
        Repository repo = gitManager.openRepository(new Project.NameKey(project));
        RevWalk rw = new RevWalk(repo);
        commit = rw.parseCommit(ObjectId.fromString(commit.getFullMessage().split("\n")[3]));

        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        df.setRepository(repo);
        df.setDiffComparator(RawTextComparator.DEFAULT);
        df.setDetectRenames(true);

        if (commit.getParentCount() == 2) {
            Git git = new Git(repo);
            LogCommand logCommand = git.log();
            if (noMerges) {
                logCommand.setRevFilter(TreeRevFilter.NO_MERGES);
            }
            Iterable<RevCommit> commits = logCommand.addRange(commit.getParents()[0], commit).call();
            for (RevCommit c : commits) {
                result.add(new CommitInfo(project, c, submitDate, df));
            }
        } else if (commit.getParentCount() == 1) {
            result.add(new CommitInfo(project, commit, submitDate, df));
        }

        return result;
    }

    private class CommitInfo {
        String project;
        String commit;
        String message;
        String authorName;
        String authorEmail;
        Date authorDate;
        String committerName;
        String committerEmail;
        Date committerDate;
        Date submitDate;
        List<String> files;

        public CommitInfo(String project, RevCommit commit, Date submitDate, DiffFormatter df) throws IOException {
            PersonIdent author = commit.getAuthorIdent();
            PersonIdent committer = commit.getCommitterIdent();
            this.project = project;
            this.commit = commit.getName();
            this.message = commit.getFullMessage();
            this.authorName = author.getName();
            this.authorEmail = author.getEmailAddress();
            this.authorDate = author.getWhen();
            this.committerName = committer.getName();
            this.committerEmail = committer.getEmailAddress();
            this.committerDate = committer.getWhen();
            this.submitDate = submitDate;

            List<DiffEntry> diffs = df.scan(commit.getParents()[0], commit);
            files = new ArrayList<>();
            for (DiffEntry diff : diffs) {
                files.add(diff.getChangeType().name() + "\t" + diff.getOldPath() + "\t" + diff.getNewPath());
            }
        }
    }
}
