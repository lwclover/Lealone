/*
 * Copyright 2004-2013 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.lealone.sql.expression.condition;

import java.util.ArrayList;
import java.util.HashSet;

import org.lealone.common.exceptions.DbException;
import org.lealone.common.util.StatementBuilder;
import org.lealone.db.ServerSession;
import org.lealone.db.value.Value;
import org.lealone.db.value.ValueBoolean;
import org.lealone.db.value.ValueNull;
import org.lealone.sql.expression.Expression;
import org.lealone.sql.expression.ExpressionColumn;
import org.lealone.sql.expression.ExpressionVisitor;
import org.lealone.sql.optimizer.ColumnResolver;
import org.lealone.sql.optimizer.IndexCondition;
import org.lealone.sql.optimizer.TableFilter;

/**
 * Used for optimised IN(...) queries where the contents of the IN list are all
 * constant and of the same type.
 * <p>
 * Checking using a HashSet is has time complexity O(1), instead of O(n) for
 * checking using an array.
 */
public class ConditionInConstantSet extends Condition {

    private Expression left;
    private int queryLevel;
    private final ArrayList<Expression> valueList;
    private final HashSet<Value> valueSet;

    /**
     * Create a new IN(..) condition.
     *
     * @param session the session
     * @param left the expression before IN
     * @param valueList the value list (at least two elements)
     */
    public ConditionInConstantSet(ServerSession session, Expression left, ArrayList<Expression> valueList) {
        this.left = left;
        this.valueList = valueList;
        this.valueSet = new HashSet<Value>(valueList.size());
        int type = left.getType();
        for (Expression expression : valueList) {
            valueSet.add(expression.getValue(session).convertTo(type));
        }
    }

    @Override
    public Value getValue(ServerSession session) {
        Value x = left.getValue(session);
        if (x == ValueNull.INSTANCE) {
            return x;
        }
        boolean result = valueSet.contains(x);
        if (!result) {
            boolean setHasNull = valueSet.contains(ValueNull.INSTANCE);
            if (setHasNull) {
                return ValueNull.INSTANCE;
            }
        }
        return ValueBoolean.get(result);
    }

    @Override
    public void mapColumns(ColumnResolver resolver, int level) {
        left.mapColumns(resolver, level);
        this.queryLevel = Math.max(level, this.queryLevel);
    }

    @Override
    public Expression optimize(ServerSession session) {
        left = left.optimize(session);
        return this;
    }

    @Override
    public void createIndexConditions(ServerSession session, TableFilter filter) {
        if (!(left instanceof ExpressionColumn)) {
            return;
        }
        ExpressionColumn l = (ExpressionColumn) left;
        if (filter != l.getTableFilter()) {
            return;
        }
        if (session.getDatabase().getSettings().optimizeInList) {
            filter.addIndexCondition(IndexCondition.getInList(l, valueList));
            return;
        }
    }

    @Override
    public void setEvaluatable(TableFilter tableFilter, boolean b) {
        left.setEvaluatable(tableFilter, b);
    }

    @Override
    public String getSQL(boolean isDistributed) {
        StatementBuilder buff = new StatementBuilder("(");
        buff.append(left.getSQL()).append(" IN(");
        for (Expression e : valueList) {
            buff.appendExceptFirst(", ");
            buff.append(e.getSQL());
        }
        return buff.append("))").toString();
    }

    @Override
    public void updateAggregate(ServerSession session) {
        // nothing to do
    }

    @Override
    public boolean isEverything(ExpressionVisitor visitor) {
        if (!left.isEverything(visitor)) {
            return false;
        }
        switch (visitor.getType()) {
        case ExpressionVisitor.OPTIMIZABLE_MIN_MAX_COUNT_ALL:
        case ExpressionVisitor.DETERMINISTIC:
        case ExpressionVisitor.INDEPENDENT:
        case ExpressionVisitor.EVALUATABLE:
        case ExpressionVisitor.SET_MAX_DATA_MODIFICATION_ID:
        case ExpressionVisitor.NOT_FROM_RESOLVER:
        case ExpressionVisitor.GET_DEPENDENCIES:
        case ExpressionVisitor.QUERY_COMPARABLE:
        case ExpressionVisitor.GET_COLUMNS:
            return true;
        default:
            throw DbException.throwInternalError("type=" + visitor.getType());
        }
    }

    @Override
    public int getCost() {
        int cost = left.getCost();
        return cost;
    }

    /**
     * Add an additional element if possible. Example: given two conditions
     * A IN(1, 2) OR A=3, the constant 3 is added: A IN(1, 2, 3).
     *
     * @param other the second condition
     * @return null if the condition was not added, or the new condition
     */
    Expression getAdditional(ServerSession session, Comparison other) {
        Expression add = other.getIfEquals(left);
        if (add != null) {
            if (add.isConstant()) {
                valueList.add(add);
                valueSet.add(add.getValue(session).convertTo(left.getType()));
                return this;
            }
        }
        return null;
    }
}
