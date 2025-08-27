package com.ssy.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
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
import java.util.List;
import java.util.stream.Collectors;

@MappedTypes({Collection.class})
@MappedJdbcTypes({JdbcType.VARCHAR})
public class CollectionTypeHandler implements TypeHandler<Collection> {

    @Override
    public void setParameter(PreparedStatement ps, int i, Collection parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null) {
            ps.setString(i, null);
        } else {
            // 转换为 JSON 格式存储
            String value = JSON.toJSONString(parameter);
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

        // 先尝试 JSON 解析
        if (value.startsWith("[") && value.endsWith("]")) {
            try {
                // 使用 FastJSON 解析
                List<String> result = JSON.parseArray(value, String.class);
                return result != null ? result : new ArrayList<>();
            } catch (JSONException e) {
                System.err.println("JSON 解析失败，尝试手动解析: " + value);
                // 手动解析 JSON 数组
                return parseJsonArray(value);
            }
        }

        // 处理逗号分隔格式
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private Collection<String> parseJsonArray(String jsonArray) {
        String content = jsonArray.substring(1, jsonArray.length() - 1); // 去掉 [ 和 ]
        if (content.trim().isEmpty()) {
            return new ArrayList<>();
        }

        return Arrays.stream(content.split(","))
                .map(String::trim)
                .map(s -> s.replaceAll("^\"|\"$", "")) // 去掉引号
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}