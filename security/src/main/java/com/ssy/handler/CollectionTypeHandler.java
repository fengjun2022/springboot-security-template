package com.ssy.handler;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeHandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

@MappedTypes({Collection.class})
@MappedJdbcTypes({JdbcType.VARCHAR})
public class CollectionTypeHandler implements TypeHandler<Collection> {

    @Override
    public void setParameter(PreparedStatement ps, int i, Collection parameter, JdbcType jdbcType) throws SQLException, SQLException {
        if (parameter == null) {
            ps.setString(i, null);
        } else {
            // 将集合转换为逗号分隔的字符串或JSON
            String value = (String) parameter.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(","));
            ps.setString(i, value);
        }
    }

    @Override
    public Collection getResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return parseCollection(value);
    }

    @Override
    public Collection getResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return parseCollection(value);
    }

    @Override
    public Collection getResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return parseCollection(value);
    }

    private Collection<String> parseCollection(String value) {
        if (value == null || value.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(value.split(","));
    }
}