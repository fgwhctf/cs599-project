package com.fgwh.repository;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DwRepository {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public DwRepository(@Qualifier("dwJdbcTemplate") JdbcTemplate jdbcTemplate, @Qualifier("dwDataSource") DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    public void validateSql(String sql) {
        jdbcTemplate.queryForList("explain " + sql);
    }

    public List<Map<String, Object>> runSql(String sql) {
        return jdbcTemplate.query(sql, rs -> {
            var metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            var rows = new java.util.ArrayList<Map<String, Object>>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        });
    }

    public Map<String, Object> getDbInfo() {
        try (var connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("dialect", metaData.getDatabaseProductName());
            info.put("version", metaData.getDatabaseProductVersion());
            return info;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to read database metadata", ex);
        }
    }
}
