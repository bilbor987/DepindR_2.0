package depindr.analyzers;

import depindr.DepinderResult;
import depindr.json.Dependency;
import depindr.model.Commit;
import depindr.model.TechnologySnapshot;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ValueOfTechForEachCommit {

    public Set<TechnologySnapshot> dependencyValueForCommits(Dependency dependency) {
        Map<Commit, List<DepinderResult>> resultsByCommitId = dependency.getResults().stream()
                .collect(Collectors.groupingBy(DepinderResult::getCommit));

        return resultsByCommitId.entrySet().stream()
                .map(entry -> {
                    int count = entry.getValue().size();
                    System.out.printf("Commit: %s technology %s is spread in %d  files \n", entry.getKey().getID(), dependency.getName(), count);

                    return TechnologySnapshot.builder()
                            .commitID(entry.getKey().getID())
                            .snapshotTimestamp(entry.getKey().getAuthorTimestamp())
                            .numberOfFiles(count)
                            .usageOfTechnology(entry.getValue().size())
                            .build();
                })
                .collect(Collectors.toSet());
    }
}