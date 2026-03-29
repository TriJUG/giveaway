package dev.trijug.giveaway.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser — no external deps, handles the simple objects/arrays the API returns.
 */
public class Json {

    public static boolean bool(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        return false;
    }

    public static Object parse(String json) {
        return new Parser(json.strip()).parseValue();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> parseArray(String json) {
        Object result = parse(json);
        if (result instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object result = parse(json);
        if (result instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    public static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? "" : v.toString();
    }

    private static class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        private List<Object> parseArray() {
            pos++; // [
            var list = new ArrayList<>();
            skipWs();
            while (pos < s.length() && s.charAt(pos) != ']') {
                list.add(parseValue());
                skipWs();
                if (pos < s.length() && s.charAt(pos) == ',') {
                    pos++;
                }
                skipWs();
            }
            if (pos < s.length()) {
                pos++; // ]
            }
            return list;
        }

        private Object parseNumber() {
            int start = pos;
            while (pos < s.length() && "-0123456789.eE+".indexOf(s.charAt(pos)) >= 0) {
                pos++;
            }
            String num = s.substring(start, pos);
            try {
                if (num.contains(".")) {
                    return Double.parseDouble(num);
                }
                return Long.parseLong(num);
            } catch (NumberFormatException e) {
                return num;
            }
        }

        private Map<String, Object> parseObject() {
            pos++; // {
            var map = new LinkedHashMap<String, Object>();
            skipWs();
            while (pos < s.length() && s.charAt(pos) != '}') {
                String key = parseString();
                skipWs();
                pos++; // :
                Object val = parseValue();
                map.put(key, val);
                skipWs();
                if (pos < s.length() && s.charAt(pos) == ',') {
                    pos++;
                }
                skipWs();
            }
            if (pos < s.length()) {
                pos++; // }
            }
            return map;
        }

        private String parseString() {
            pos++; // opening "
            var sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') {
                    break;
                }
                if (c == '\\' && pos < s.length()) {
                    char esc = s.charAt(pos++);
                    sb.append(switch (esc) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'u' -> {
                            if (pos + 4 <= s.length()) {
                                char decoded = (char) Integer.parseInt(s.substring(pos, pos + 4), 16);
                                pos += 4;
                                yield decoded;
                            }
                            yield 'u';
                        }
                        default -> esc;
                    });
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Object parseValue() {
            skipWs();
            if (pos >= s.length()) {
                return null;
            }
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> {
                    pos += 4;
                    yield Boolean.TRUE;
                }
                case 'f' -> {
                    pos += 5;
                    yield Boolean.FALSE;
                }
                case 'n' -> {
                    pos += 4;
                    yield null;
                }
                default -> parseNumber();
            };
        }

        private void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }
    }
}
