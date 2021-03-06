package depindr;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class DepinderFileTest {

    @Test
    public void getLineNumberOfAbsoluteCharacterIndex() throws IOException {
        String contentOfResourceFile = getContentOfResourceFile("Test.java");

        DepinderFile depinderFile = DepinderFile.builder().content(contentOfResourceFile).build();

        assertEquals(15, depinderFile.getLineNumberOfAbsoluteCharacterIndex(322));
    }

    private String getContentOfResourceFile(String resourceFile) throws IOException {
        String filePathAsString = getClass().getClassLoader().getResource(resourceFile).getPath();
        File file = new File(filePathAsString);

        return new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
    }
}