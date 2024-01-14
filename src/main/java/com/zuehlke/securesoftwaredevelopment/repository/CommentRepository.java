package com.zuehlke.securesoftwaredevelopment.repository;

import com.zuehlke.securesoftwaredevelopment.domain.Comment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class CommentRepository {

    private static final Logger LOG = LoggerFactory.getLogger(CommentRepository.class);


    private DataSource dataSource;

    public CommentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void create(Comment comment) {
        // The following code was once upon a time vulnerable to SQLi
        // SQLiLovesXSSLovesJESSIONID'); insert into persons values (666, 'NotoriousAcko', 'hackerZ', '<img src="x" onerror="console.log(document.cookie)"/>'); --
        /*
        String query = "insert into comments(giftId, userId, comment) values (" + comment.getGiftId() + ", " + comment.getUserId() + ", '" + comment.getComment() + "')";

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
        ) {
            statement.execute(query);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        */

        String query = "insert into comments(giftId, userId, comment) values (?, ?, ?)";
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setInt(1, comment.getGiftId());
            preparedStatement.setInt(2, comment.getUserId());
            preparedStatement.setString(3, comment.getComment());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            // e.printStackTrace();
            String errorMessage = "Failed to insert comment: " + comment;
            LOG.error(errorMessage, e);
        }
    }

    public List<Comment> getAll(String giftId) {
        List<Comment> commentList = new ArrayList<>();
        String query = "SELECT giftId, userId, comment FROM comments WHERE giftId = " + giftId;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            while (rs.next()) {
                commentList.add(new Comment(rs.getInt(1), rs.getInt(2), rs.getString(3)));
            }
        } catch (SQLException e) {
            // e.printStackTrace();
            String errorMessage = "Failed to fetch comments for gift: " + giftId;
            LOG.error(errorMessage, e);
        }
        return commentList;
    }
}
