package com.fgwh.repository;

import com.fgwh.model.ColumnInfo;
import com.fgwh.model.TableInfo;
import com.fgwh.util.JsonSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MetaRepository {

    private final JdbcTemplate jdbcTemplate;

    public MetaRepository(@Qualifier("metaJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ColumnInfo findColumnById(String id) {
        List<ColumnInfo> result = jdbcTemplate.query(
                "select id, name, type, role, examples, description, alias, table_id from column_info where id = ?",
                (rs, rowNum) -> new ColumnInfo(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getString("role"),
                        parseList(rs.getString("examples"), new TypeReference<List<Object>>() {
                        }),
                        rs.getString("description"),
                        parseList(rs.getString("alias"), new TypeReference<List<String>>() {
                        }),
                        rs.getString("table_id")
                ),
                id
        );
        return result.isEmpty() ? null : result.getFirst();
    }

    public TableInfo findTableById(String id) {
        List<TableInfo> result = jdbcTemplate.query(
                "select id, name, role, description from table_info where id = ?",
                (rs, rowNum) -> new TableInfo(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("role"),
                        rs.getString("description")
                ),
                id
        );
        return result.isEmpty() ? null : result.getFirst();
    }

    public List<ColumnInfo> findKeyColumnsByTableId(String tableId) {
        return jdbcTemplate.query(
                "select id, name, type, role, examples, description, alias, table_id from column_info where table_id = ? and role in ('primary_key','foreign_key')",
                (rs, rowNum) -> new ColumnInfo(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getString("role"),
                        parseList(rs.getString("examples"), new TypeReference<List<Object>>() {
                        }),
                        rs.getString("description"),
                        parseList(rs.getString("alias"), new TypeReference<List<String>>() {
                        }),
                        rs.getString("table_id")
                ),
                tableId
        );
    }

    private static <T> T parseList(String json, TypeReference<T> typeReference) {
        if (json == null || json.isBlank()) {
            return JsonSupport.read("[]", typeReference);
        }
        return JsonSupport.read(json, typeReference);
    }
}
