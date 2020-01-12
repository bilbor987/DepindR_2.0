package depindr.model;

import depindr.DepinderResult;
import depindr.model.dto.AuthorDTO;

import java.util.ArrayList;
import java.util.List;

public class Author implements Entity<AuthorID> {

    private AuthorID authorID;

    private List<DepinderResult> results = new ArrayList<>();

    public Author(AuthorID authorID) {
        this.authorID = authorID;
    }

    public static Author fromDTO(AuthorDTO authorDTO) {
        return new Author(new AuthorID(authorDTO.getName(), authorDTO.getEmail()));
    }

    @Override
    public AuthorID getID() {
        return authorID;
    }

    public void addResult(DepinderResult depinderResult) {
        results.add(depinderResult);
    }
}
