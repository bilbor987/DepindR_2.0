package depindr;


import depindr.configuration.DepinderConfiguration;
import depindr.constants.DepinderConstants;
import depindr.exceptions.DepinderException;
import depindr.git.GitClient;
import depindr.json.Dependency;
import depindr.json.JsonFingerprintParser;
import depindr.model.*;
import depindr.model.dto.CommitDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static depindr.utils.FileUtils.removeComments;

/*
 * #DONE 0.Read configuration file
 * #DONE 1.Read dependecies from .json file and print on console the file
 * #DONE 2. Read repository using JGit (checkout commits, read al files, create commit object, match Dependencies on all files)
 * */

@Slf4j
public class Depinder {
    public static void main(String[] args) {
        System.out.print("Reading configuration file: \n");

        Path configurationFilePath = Paths.get(DepinderConstants.CONFIGURATION_FOLDER, DepinderConstants.CONFIGURATION_FILE);

        File resultsFolder = new File(DepinderConstants.RESULTS_FOLDER);
        if (!resultsFolder.exists())
            resultsFolder.mkdirs();

        Properties properties = new Properties();
        try {
            properties.load(new FileReader(configurationFilePath.toFile()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        DepinderConfiguration.loadProperties(properties);

        String rootFolder = DepinderConfiguration.getInstance().getProperty(DepinderConstants.ROOT_FOLDER);
        String branchName = DepinderConfiguration.getInstance().getProperty(DepinderConstants.BRANCH);
        String removeCommentsFlag = DepinderConfiguration.getInstance().getProperty(DepinderConstants.REMOVE_COMMENTS);
        String technologyToSearchFor = DepinderConfiguration.getInstance().getProperty(DepinderConstants.TECH_TO_SEARCH_FOR);

        AuthorRegistry authorRegistry = new AuthorRegistry();
        CommitRegistry commitRegistry = new CommitRegistry();
        DependencyRegistry dependencyRegistry = new DependencyRegistry();

        List<Dependency> dependencies = readDependencyFingerprints();
        dependencyRegistry.addAll(dependencies);

        //check if root folder is a git repository
        GitClient gitClient = new GitClient();

        gitClient.checkoutCommitForRepo(rootFolder, branchName);

        List<CommitDTO> allCommitNames = gitClient.getAllCommits(rootFolder);

        //checkout each commit, read files, match fingerprints, create models.
        for (CommitDTO commitDTO : allCommitNames) {
            Author author = Author.fromDTO(commitDTO.getAuthor());
            author = authorRegistry.add(author);

            FileRegistry fileRegistry = new FileRegistry();

            Commit commit = Commit.fromDTO(commitDTO);
            commit.setAuthor(author);
            commit = commitRegistry.add(commit);
            commit.setFileRegistry(fileRegistry);

            gitClient.checkoutCommitForRepo(rootFolder, commitDTO.getCommitID());

            List<Path> modifiedFilePaths = commitDTO.getModifiedFiles().stream().map(s -> Paths.get(rootFolder, s)).collect(Collectors.toList());
            List<DepinderFile> depinderFiles = readFilesFromRepo(rootFolder, modifiedFilePaths, fileRegistry, removeCommentsFlag);
            for (Dependency dependency : dependencies) {
                for (DepinderFile depinderFile : depinderFiles) {
                    DepinderResult depinderResult = dependency.analyze(depinderFile);
                    if (depinderResult != null) {
                        depinderResult.setCommit(commit);
                        commit.addResult(depinderResult);
                        depinderResult.setAuthor(author);
                        author.addResult(depinderResult);
                    }
                }
            }

            fileRegistry.getAll().forEach(depinderFile -> depinderFile.setContent(null));
        }

        System.out.println("gata");


        //technologyWithinFiles(commitRegistry, dependencyRegistry, technologyToSearchFor);

        //whenDidATechAppear(dependencyRegistry);

        evolutionOfATech(commitRegistry, dependencyRegistry, technologyToSearchFor);
    }

    private static void whenDidATechAppear(DependencyRegistry dependencyRegistry) {
        dependencyRegistry.getAll().forEach(dependency -> {
            List<Commit> commits = dependency.getDepinderResults().stream()
                    .map(DepinderResult::getCommit)
                    .sorted(Comparator.comparing(Commit::getAuthorTimestamp))
                    .collect(Collectors.toList());

            commits.stream().findFirst().ifPresent(commit -> System.out.printf("Dependency %s appeared in commit %s on %s%n", dependency.getName(), commit.getID(), commit.getAuthorTimestamp().toString()));
        });
    }

    //#TODO maybe would be usefull to know at a certain commit, now it's by default at the last commit
    private static void technologyWithinFiles(CommitRegistry commitRegistry, DependencyRegistry dependencyRegistry, String tech) {
        Commit lastCommit = commitRegistry.getLastCommit().get();
        dependencyRegistry.getAll().forEach(dependency -> {
            List<String> filesWithCertainTech = dependency.getDepinderResults().stream()
                    .filter(depinderResult -> depinderResult.getCommit().equals(lastCommit))
                    .map(DepinderResult::getDepinderFile)
                    .map(DepinderFile::getName)
                    .filter(s -> dependency.getName().equals(tech))
                    .collect(Collectors.toList());

            if (!filesWithCertainTech.isEmpty()) {
                System.out.printf("Technology %s is spread in %d files \n", dependency.getName(), filesWithCertainTech.size());
                System.out.printf("%s: has %s \n", dependency.getName(), filesWithCertainTech.toString());
            }
        });
    }

    private static void evolutionOfATech(CommitRegistry commitRegistry, DependencyRegistry dependencyRegistry, String tech) {
        List<Commit> allCommits = new ArrayList<>(commitRegistry.getAll());
        dependencyRegistry.getAll().forEach(dependency -> {
            allCommits.forEach(currentCommit -> {
                List<String> filesWithCertainTech = dependency.getDepinderResults().stream()
                        .filter(depinderResult -> depinderResult.getCommit().equals(currentCommit))
                        .map(DepinderResult::getDepinderFile)
                        .map(DepinderFile::getName)
                        .filter(s -> dependency.getName().equals(tech))
                        .collect(Collectors.toList());

                if (!filesWithCertainTech.isEmpty()) {
                    System.out.printf("Commit: %s technology %s is spread in %d files \n", currentCommit.getID(), dependency.getName(), filesWithCertainTech.size());
                }

            });
        });

    }


    private static List<DepinderFile> readFilesFromRepo(String rootFolder, List<Path> filesToRead, FileRegistry fileRegistry, String flag) {
        try {
            return Files.walk(Paths.get(rootFolder))
                    .filter(Files::isRegularFile)
                    .filter(filesToRead::contains)
                    .map(path -> {
                        try {
                            DepinderFile depinderFile = DepinderFile.builder()
                                    .path(path.toAbsolutePath().toString())
                                    .name(path.getFileName().toString())
                                    .extension(FilenameUtils.getExtension(path.getFileName().toString()))
                                    .content(readFileContentWithLfEnding(path, flag))
                                    .build();
                            fileRegistry.add(depinderFile);
                            return depinderFile;
                        } catch (IOException e) {
                            e.printStackTrace();
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new DepinderException("Could not read from folder " + rootFolder, e);
        }
    }

    private static String readFileContentWithLfEnding(Path path, String flag) throws IOException {
        String s = new String(Files.readAllBytes(path))
                .replaceAll("\\r\\n", "\n") //remove Windows line endings
                .replaceAll("\\r", "\n"); //remove Mac line endings
        if (flag.equals("true"))
            s = removeComments(s);
        return s;
    }

    private static List<Dependency> readDependencyFingerprints() {
        String rootFolder = DepinderConfiguration.getInstance().getProperty(DepinderConstants.JSON_FINGERPRINT_FILES);

        JsonFingerprintParser jsonFingerprintParser = new JsonFingerprintParser();
        return jsonFingerprintParser.parseTechnologiesFile(rootFolder);
    }

}
