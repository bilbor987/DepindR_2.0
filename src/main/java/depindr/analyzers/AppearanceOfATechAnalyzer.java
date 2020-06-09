package depindr.analyzers;

import depindr.Depinder;
import depindr.DepinderResult;
import depindr.json.Dependency;
import depindr.model.entity.Commit;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AppearanceOfATechAnalyzer implements DepinderCommand {

    public void execute(Depinder depinder, String[] args) {

        depinder.getDependencyRegistry().getAll().forEach(dependency -> {
            List<Commit> commits = getCommits(dependency);

            commits.stream().findFirst().ifPresent(commit ->
                    System.out.printf("Dependency %s appeared in commit %s on %s%n authored by %s\n",
                            dependency.getName(), commit.getID(), commit.getAuthorTimestamp().toString(), commit.getAuthor().getID().getName())
            );
        });
    }

    @NotNull
    private List<Commit> getCommits(Dependency dependency) {
        return dependency.getDepinderResults().stream()
                .map(DepinderResult::getCommit)
                .sorted(Comparator.comparing(Commit::getAuthorTimestamp))
                .collect(Collectors.toList());
    }

    @Override
    public boolean parse(String[] args) {
        return args.length == 3;
    }

    @Override
    public String usage() {
        return "depinder --appearance <json_output_name_for_results> <flag_for_removal_of_comments>";
    }

}
