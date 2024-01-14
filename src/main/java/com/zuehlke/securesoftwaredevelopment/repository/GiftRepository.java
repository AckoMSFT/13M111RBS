package com.zuehlke.securesoftwaredevelopment.repository;

import com.zuehlke.securesoftwaredevelopment.config.AuditLogger;
import com.zuehlke.securesoftwaredevelopment.config.Entity;
import com.zuehlke.securesoftwaredevelopment.config.SecurityUtil;
import com.zuehlke.securesoftwaredevelopment.domain.Tag;
import com.zuehlke.securesoftwaredevelopment.domain.Gift;
import com.zuehlke.securesoftwaredevelopment.domain.NewGift;
import com.zuehlke.securesoftwaredevelopment.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class GiftRepository {

    private static final Logger LOG = LoggerFactory.getLogger(GiftRepository.class);
    private static final AuditLogger auditLogger = AuditLogger.getAuditLogger(GiftRepository.class);

    private DataSource dataSource;

    public GiftRepository(DataSource dataSource) {

        this.dataSource = dataSource;
    }

    public List<Gift> getAll() {
        List<Gift> giftList = new ArrayList<>();
        String query = "SELECT id, name, description, price FROM gift";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            while (rs.next()) {
                Gift gift = createGiftFromResultSet(rs);
                giftList.add(gift);
            }
        } catch (SQLException e) {
            // e.printStackTrace();
            String errorMessage = "Failed to fetch gifts.";
            LOG.error(errorMessage, e);
        }
        return giftList;
    }

    public List<Gift> search(String searchTerm) throws SQLException {
        List<Gift> giftList = new ArrayList<>();
        String query = "SELECT DISTINCT g.id, g.name, g.description, g.price FROM gift g, gift_to_tag gt, tags t" +
                " WHERE g.id = gt.giftId" +
                " AND gt.tagId = t.id" +
                " AND (UPPER(g.name) like UPPER('%" + searchTerm + "%')" +
                " OR UPPER(t.name) like UPPER('%" + searchTerm + "%'))";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            while (rs.next()) {
                giftList.add(createGiftFromResultSet(rs));
            }
        }
        return giftList;
    }

    public Gift get(int giftId, List<Tag> tagList) {
        String query = "SELECT id, name, description, price FROM gift WHERE id = " + giftId;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            while (rs.next()) {
                Gift gift = createGiftFromResultSet(rs);
                List<Tag> giftTags = new ArrayList<>();
                String query2 = "SELECT giftId, tagId FROM gift_to_tag WHERE giftId = " + giftId;
                ResultSet rs2 = statement.executeQuery(query2);
                while (rs2.next()) {
                    Tag tag = tagList.stream().filter(g -> {
                        try {
                            return g.getId() == rs2.getInt(2);
                        } catch (SQLException e) {
                            String errorMessage = "Failed to fetch tags for gift with id: " + giftId;
                            LOG.error(errorMessage, e);
                            throw new RuntimeException(e);
                        }
                    }).findFirst().get();
                    giftTags.add(tag);
                }
                gift.setTags(giftTags);
                return gift;
            }
        } catch (SQLException e) {
            // e.printStackTrace();
            String errorMessage = "Failed to fetch gift with id: " + giftId + " and tags: " + tagList;
            LOG.error(errorMessage, e);
        }

        return null;
    }

    public long create(NewGift gift, List<Tag> tagsToInsert) {
        String query = "INSERT INTO gift(name, description, price) VALUES(?, ?, ?)";
        long id = 0;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        ) {
            statement.setString(1, gift.getName());
            statement.setString(2, gift.getDescription());
            statement.setDouble(3, gift.getPrice());

            User user = SecurityUtil.getCurrentUser();
            Entity entity = new Entity("gift.insert", String.valueOf(user.getId()), "n/a", gift.toString());
            AuditLogger.getAuditLogger(CommentRepository.class).auditChange(entity);

            statement.executeUpdate();
            ResultSet generatedKeys = statement.getGeneratedKeys();
            if (generatedKeys.next()) {
                id = generatedKeys.getLong(1);
                long finalId = id;
                tagsToInsert.stream().forEach(tag -> {
                    String query2 = "INSERT INTO gift_to_tag(giftId, tagId) VALUES (?, ?)";
                    try (PreparedStatement statement2 = connection.prepareStatement(query2);
                    ) {
                        statement2.setInt(1, (int) finalId);
                        statement2.setInt(2, tag.getId());

                        Entity entityTags = new Entity("gift.tag.insert", String.valueOf(user.getId()), "n/a", "(" + finalId + ", " + tag.getId() + ")");
                        AuditLogger.getAuditLogger(CommentRepository.class).auditChange(entityTags);

                        statement2.executeUpdate();
                    } catch (SQLException e) {
                        // e.printStackTrace();
                        String errorMessage = "Failed to insert gift_to_tag mapping for gift: " + finalId + " and tag: " + tag;
                        LOG.error(errorMessage, e);
                    }
                });
            }
        } catch (SQLException e) {
            // e.printStackTrace();
            String errorMessage = "Failed to create gift: " + gift + " with tags: " + tagsToInsert;
            LOG.error(errorMessage, e);
        }
        return id;
    }

    public void delete(int giftId) {
        String query = "DELETE FROM gift WHERE id = " + giftId;
        String query2 = "DELETE FROM ratings WHERE giftId = " + giftId;
        String query3 = "DELETE FROM comments WHERE giftId = " + giftId;
        String query4 = "DELETE FROM gift_to_tag WHERE giftId = " + giftId;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
        ) {
            User user = SecurityUtil.getCurrentUser();
            Entity entityGift = new Entity("gift.delete", String.valueOf(user.getId()), String.valueOf(giftId), "n/a");
            AuditLogger.getAuditLogger(CommentRepository.class).auditChange(entityGift);

            statement.executeUpdate(query);

            Entity entityRatings = new Entity("ratings.delete", String.valueOf(user.getId()), String.valueOf(giftId), "n/a");
            AuditLogger.getAuditLogger(CommentRepository.class).auditChange(entityRatings);

            statement.executeUpdate(query2);

            Entity entityComments = new Entity("comments.delete", String.valueOf(user.getId()), String.valueOf(giftId), "n/a");
            AuditLogger.getAuditLogger(CommentRepository.class).auditChange(entityComments);

            statement.executeUpdate(query3);

            Entity entityGiftTags = new Entity("gift.tags.delete", String.valueOf(user.getId()), String.valueOf(giftId), "n/a");
            AuditLogger.getAuditLogger(CommentRepository.class).auditChange(entityGiftTags);

            statement.executeUpdate(query4);
        } catch (SQLException e) {
            // e.printStackTrace();
            String errorMessage = "Failed to delete gift: " + giftId;
            LOG.error(errorMessage, e);
        }
    }

    private Gift createGiftFromResultSet(ResultSet rs) throws SQLException {
        int id = rs.getInt(1);
        String name = rs.getString(2);
        String description = rs.getString(3);
        double price = rs.getDouble(4);
        return new Gift(id, name, description, price, new ArrayList<>());
    }
}
