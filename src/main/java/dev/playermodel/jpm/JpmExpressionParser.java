package dev.playermodel.jpm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parser for the expression language used by OptiFine/EMF JPM animations.
 * Supports arithmetic, comparisons, boolean operators, ternaries and the functions used by
 * JustExpressions (clamp, sin/cos, if, random, torad, ...).
 */
public final class JpmExpressionParser {
    private JpmExpressionParser() {
    }

    public static JpmExpression parse(String source) throws JpmException {
        if (source == null || source.isBlank()) {
            return context -> 0;
        }
        Parser parser = new Parser(source);
        JpmExpression expression = parser.parseTernary();
        if (parser.peek().type != TokenType.END) {
            throw new JpmException("JPM 表达式有多余内容，位置 " + parser.peek().position + ": " + source);
        }
        return expression;
    }

    private enum TokenType { NUMBER, IDENT, OP, END }

    private record Token(TokenType type, String text, int position) {
    }

    private static final class Lexer {
        private final String source;
        private int index;

        Lexer(String source) {
            this.source = source;
        }

        Token next() throws JpmException {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
            if (index >= source.length()) return new Token(TokenType.END, "", index);
            int start = index;
            char c = source.charAt(index);
            if (Character.isDigit(c) || c == '.') {
                index++;
                while (index < source.length()) {
                    char n = source.charAt(index);
                    if (Character.isDigit(n) || n == '.' || n == 'e' || n == 'E'
                            || ((n == '+' || n == '-') && index > start
                            && (source.charAt(index - 1) == 'e' || source.charAt(index - 1) == 'E'))) {
                        index++;
                    } else {
                        break;
                    }
                }
                return new Token(TokenType.NUMBER, source.substring(start, index), start);
            }
            if (Character.isLetter(c) || c == '_' || c == '$') {
                index++;
                while (index < source.length()) {
                    char n = source.charAt(index);
                    if (Character.isLetterOrDigit(n) || n == '_' || n == '.' || n == '$') index++;
                    else break;
                }
                return new Token(TokenType.IDENT, source.substring(start, index), start);
            }
            if (index + 1 < source.length()) {
                String pair = source.substring(index, index + 2);
                if (pair.equals("&&") || pair.equals("||") || pair.equals("==")
                        || pair.equals("!=") || pair.equals("<=") || pair.equals(">=")) {
                    index += 2;
                    return new Token(TokenType.OP, pair, start);
                }
            }
            if ("+-*/%^<>=!?:,()".indexOf(c) >= 0) {
                index++;
                return new Token(TokenType.OP, String.valueOf(c), start);
            }
            throw new JpmException("JPM 表达式无法识别字符 '" + c + "'，位置 " + index + ": " + source);
        }
    }

    private static final class Parser {
        private final Lexer lexer;
        private Token current;

        Parser(String source) throws JpmException {
            lexer = new Lexer(source);
            current = lexer.next();
        }

        Token peek() {
            return current;
        }

        private void advance() throws JpmException {
            current = lexer.next();
        }

        private boolean match(String token) throws JpmException {
            if (current.type == TokenType.OP && current.text.equals(token)) {
                advance();
                return true;
            }
            return false;
        }

        private void expect(String token) throws JpmException {
            if (!match(token)) {
                throw new JpmException("JPM 表达式缺少 '" + token + "'，位置 "
                        + current.position + "，实际为 '" + current.text + "'");
            }
        }

        JpmExpression parseTernary() throws JpmException {
            JpmExpression condition = parseOr();
            if (!match("?")) return condition;
            JpmExpression whenTrue = parseTernary();
            expect(":");
            JpmExpression whenFalse = parseTernary();
            return context -> condition.eval(context) != 0 ? whenTrue.eval(context) : whenFalse.eval(context);
        }

        private JpmExpression parseOr() throws JpmException {
            JpmExpression left = parseAnd();
            while (match("||")) {
                JpmExpression right = parseAnd();
                JpmExpression a = left;
                left = context -> (a.eval(context) != 0 || right.eval(context) != 0) ? 1 : 0;
            }
            return left;
        }

        private JpmExpression parseAnd() throws JpmException {
            JpmExpression left = parseEquality();
            while (match("&&")) {
                JpmExpression right = parseEquality();
                JpmExpression a = left;
                left = context -> (a.eval(context) != 0 && right.eval(context) != 0) ? 1 : 0;
            }
            return left;
        }

        private JpmExpression parseEquality() throws JpmException {
            JpmExpression left = parseComparison();
            while (current.type == TokenType.OP && (current.text.equals("==") || current.text.equals("!="))) {
                String op = current.text;
                advance();
                JpmExpression right = parseComparison();
                JpmExpression a = left;
                left = context -> {
                    boolean equal = Math.abs(a.eval(context) - right.eval(context)) < 1e-9;
                    return (op.equals("==") ? equal : !equal) ? 1 : 0;
                };
            }
            return left;
        }

        private JpmExpression parseComparison() throws JpmException {
            JpmExpression left = parseAdditive();
            while (current.type == TokenType.OP && SetOfComparisons.CONTAINS.contains(current.text)) {
                String op = current.text;
                advance();
                JpmExpression right = parseAdditive();
                JpmExpression a = left;
                left = context -> {
                    double x = a.eval(context);
                    double y = right.eval(context);
                    return switch (op) {
                        case "<" -> x < y ? 1 : 0;
                        case "<=" -> x <= y ? 1 : 0;
                        case ">" -> x > y ? 1 : 0;
                        default -> x >= y ? 1 : 0;
                    };
                };
            }
            return left;
        }

        private JpmExpression parseAdditive() throws JpmException {
            JpmExpression left = parseMultiplicative();
            while (current.type == TokenType.OP && (current.text.equals("+") || current.text.equals("-"))) {
                String op = current.text;
                advance();
                JpmExpression right = parseMultiplicative();
                JpmExpression a = left;
                left = context -> op.equals("+") ? a.eval(context) + right.eval(context)
                        : a.eval(context) - right.eval(context);
            }
            return left;
        }

        private JpmExpression parseMultiplicative() throws JpmException {
            JpmExpression left = parsePower();
            while (current.type == TokenType.OP
                    && (current.text.equals("*") || current.text.equals("/") || current.text.equals("%"))) {
                String op = current.text;
                advance();
                JpmExpression right = parsePower();
                JpmExpression a = left;
                left = context -> {
                    double x = a.eval(context);
                    double y = right.eval(context);
                    return switch (op) {
                        case "*" -> x * y;
                        case "/" -> y == 0 ? 0 : x / y;
                        default -> y == 0 ? 0 : x % y;
                    };
                };
            }
            return left;
        }

        private JpmExpression parsePower() throws JpmException {
            JpmExpression left = parseUnary();
            if (match("^")) {
                JpmExpression right = parsePower();
                JpmExpression a = left;
                return context -> Math.pow(a.eval(context), right.eval(context));
            }
            return left;
        }

        private JpmExpression parseUnary() throws JpmException {
            if (match("+")) return parseUnary();
            if (match("-")) {
                JpmExpression inner = parseUnary();
                return context -> -inner.eval(context);
            }
            if (match("!")) {
                JpmExpression inner = parseUnary();
                return context -> inner.eval(context) == 0 ? 1 : 0;
            }
            return parsePrimary();
        }

        private JpmExpression parsePrimary() throws JpmException {
            Token token = current;
            if (token.type == TokenType.NUMBER) {
                advance();
                try {
                    double value = Double.parseDouble(token.text);
                    return context -> value;
                } catch (NumberFormatException e) {
                    throw new JpmException("JPM 表达式数字无效: " + token.text, e);
                }
            }
            if (token.type == TokenType.IDENT) {
                advance();
                String name = token.text;
                if (match("(")) {
                    List<JpmExpression> args = new ArrayList<>();
                    if (!match(")")) {
                        do {
                            args.add(parseTernary());
                        } while (match(","));
                        expect(")");
                    }
                    return function(name, args, token.position);
                }
                return context -> context.value(name);
            }
            if (match("(")) {
                JpmExpression inner = parseTernary();
                expect(")");
                return inner;
            }
            throw new JpmException("JPM 表达式缺少值，位置 " + token.position + "，实际为 '" + token.text + "'");
        }

        private JpmExpression function(String rawName, List<JpmExpression> args, int position) throws JpmException {
            String name = rawName.toLowerCase(Locale.ROOT);
            return switch (name) {
                case "if" -> {
                    require(name, args, 3, position);
                    yield context -> args.get(0).eval(context) != 0
                            ? args.get(1).eval(context) : args.get(2).eval(context);
                }
                case "clamp" -> {
                    require(name, args, 3, position);
                    yield context -> {
                        double value = args.get(0).eval(context);
                        double min = args.get(1).eval(context);
                        double max = args.get(2).eval(context);
                        double low = Math.min(min, max);
                        double high = Math.max(min, max);
                        return Math.max(low, Math.min(high, value));
                    };
                }
                case "random" -> {
                    requireAtMost(name, args, 1, position);
                    yield context -> context.random(args.isEmpty() ? context.id : args.get(0).eval(context));
                }
                case "sin" -> unaryMath(name, args, position, Math::sin);
                case "cos" -> unaryMath(name, args, position, Math::cos);
                case "tan" -> unaryMath(name, args, position, Math::tan);
                case "asin" -> unaryMath(name, args, position, Math::asin);
                case "acos" -> unaryMath(name, args, position, Math::acos);
                case "atan" -> unaryMath(name, args, position, Math::atan);
                case "sqrt" -> unaryMath(name, args, position, Math::sqrt);
                case "abs" -> unaryMath(name, args, position, Math::abs);
                case "floor" -> unaryMath(name, args, position, Math::floor);
                case "ceil" -> unaryMath(name, args, position, Math::ceil);
                case "round" -> unaryMath(name, args, position, value -> Math.round(value));
                case "torad" -> unaryMath(name, args, position, Math::toRadians);
                case "todeg" -> unaryMath(name, args, position, Math::toDegrees);
                case "exp" -> unaryMath(name, args, position, Math::exp);
                case "log" -> unaryMath(name, args, position, Math::log);
                case "atan2" -> {
                    require(name, args, 2, position);
                    yield context -> Math.atan2(args.get(0).eval(context), args.get(1).eval(context));
                }
                case "pow" -> {
                    require(name, args, 2, position);
                    yield context -> Math.pow(args.get(0).eval(context), args.get(1).eval(context));
                }
                case "fmod" -> {
                    require(name, args, 2, position);
                    yield context -> args.get(1).eval(context) == 0 ? 0
                            : args.get(0).eval(context) % args.get(1).eval(context);
                }
                case "lerp" -> {
                    require(name, args, 3, position);
                    yield context -> {
                        double a = args.get(0).eval(context);
                        double b = args.get(1).eval(context);
                        return a + (b - a) * args.get(2).eval(context);
                    };
                }
                case "frac" -> unaryMath(name, args, position, value -> value - Math.floor(value));
                case "signum" -> unaryMath(name, args, position, Math::signum);
                case "wrapdeg" -> unaryMath(name, args, position, value -> wrapDegrees((float) value));
                case "wraprad" -> unaryMath(name, args, position, value -> org.joml.Math.toRadians(wrapDegrees((float) org.joml.Math.toDegrees(value))));
                case "degdiff" -> {
                    require(name, args, 2, position);
                    yield context -> wrapDegrees((float) (args.get(0).eval(context) - args.get(1).eval(context)));
                }
                case "raddiff" -> {
                    require(name, args, 2, position);
                    yield context -> org.joml.Math.toRadians(wrapDegrees(
                            (float) org.joml.Math.toDegrees(args.get(0).eval(context) - args.get(1).eval(context))));
                }
                case "in" -> {
                    requireAtLeast(name, args, 2, position);
                    yield context -> {
                        double value = args.get(0).eval(context);
                        for (int i = 1; i < args.size(); i++) {
                            if (Math.abs(value - args.get(i).eval(context)) < 1e-9) return 1;
                        }
                        return 0;
                    };
                }
                case "between" -> {
                    require(name, args, 3, position);
                    yield context -> {
                        double value = args.get(0).eval(context);
                        double low = Math.min(args.get(1).eval(context), args.get(2).eval(context));
                        double high = Math.max(args.get(1).eval(context), args.get(2).eval(context));
                        return value >= low && value <= high ? 1 : 0;
                    };
                }
                case "equals" -> {
                    require(name, args, 2, position);
                    yield context -> Math.abs(args.get(0).eval(context) - args.get(1).eval(context)) < 1e-9 ? 1 : 0;
                }
                case "ifb" -> {
                    require(name, args, 3, position);
                    yield context -> args.get(0).eval(context) != 0
                            ? args.get(1).eval(context) : args.get(2).eval(context);
                }
                case "min", "max" -> {
                    requireAtLeast(name, args, 1, position);
                    yield context -> {
                        double value = args.get(0).eval(context);
                        for (int i = 1; i < args.size(); i++) {
                            double next = args.get(i).eval(context);
                            value = name.equals("min") ? Math.min(value, next) : Math.max(value, next);
                        }
                        return value;
                    };
                }
                default -> throw new JpmException("JPM 表达式不支持函数 '" + rawName + "'，位置 " + position);
            };
        }

        private static float wrapDegrees(float value) {
            float wrapped = value % 360.0F;
            if (wrapped >= 180.0F) wrapped -= 360.0F;
            if (wrapped < -180.0F) wrapped += 360.0F;
            return wrapped;
        }

        private JpmExpression unaryMath(String name, List<JpmExpression> args, int position,
                                        java.util.function.DoubleUnaryOperator op) throws JpmException {
            require(name, args, 1, position);
            return context -> op.applyAsDouble(args.get(0).eval(context));
        }

        private void require(String name, List<JpmExpression> args, int count, int position) throws JpmException {
            if (args.size() != count) {
                throw new JpmException("JPM 函数 " + name + " 需要 " + count + " 个参数，位置 " + position);
            }
        }

        private void requireAtMost(String name, List<JpmExpression> args, int count, int position) throws JpmException {
            if (args.size() > count) {
                throw new JpmException("JPM 函数 " + name + " 最多需要 " + count + " 个参数，位置 " + position);
            }
        }

        private void requireAtLeast(String name, List<JpmExpression> args, int count, int position) throws JpmException {
            if (args.size() < count) {
                throw new JpmException("JPM 函数 " + name + " 至少需要 " + count + " 个参数，位置 " + position);
            }
        }
    }

    private static final class SetOfComparisons {
        static final java.util.Set<String> CONTAINS = java.util.Set.of("<", "<=", ">", ">=");
    }
}
