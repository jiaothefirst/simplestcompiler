package com.example.compiler.grammer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.example.compiler.grammer.Expr.*;
import static com.example.compiler.grammer.Statement.*;

/**
 * execute a func
 */
public class Executor {
    /**
     * all functions
     */
    private static Map<String, FuncStatement> userFuncs = null;

    public static void setUserFuncs(Map<String, FuncStatement> funcs) {
        userFuncs = funcs;
    }
    private final FuncStatement fs;
    private Env env;
    /**
     * executed return statement
     */
    private boolean returned = false;
    private LocalVar returnValue = null;
    /**
     * loop statement's level
     */
    private int circleLevel = 0;
    /**
     * break statement's circle level
     */
    private int breakLevel = CLEAR_MARK;
    /**
     * continue statement's circle level
     */
    private int continueLevel = CLEAR_MARK;
    /**
     * 0 means false, not 0 means true
      */
    private static final int FALSE = 0;
    private static final int TRUE = 1;
    /**
     * 清除 break，continue标记
     */
    private static final int CLEAR_MARK = -1;

    private boolean inCircle() {
        return circleLevel != 0;
    }


    public Executor(Env env, FuncStatement fs) {
        this.env = env;
        this.fs = fs;
    }

    public LocalVar execute() throws Exception {
        executeBlock(fs.block);
        return returnValue;
    }

    private void executeStatement(Statement statement) throws Exception {
        String className = statement.getClass().getSimpleName();
        switch (className){
            case "DefineStatement": executeDefineStatement((DefineStatement) statement);break;
            case "AssignStatement": executeAssignStatement((AssignStatement) statement);break;
            case "IfStatement": executeIfStatement((IfStatement) statement);break;
            case "WhileStatement": executeWhileStatement((WhileStatement) statement);break;
            case "DoWhileStatement": executeDoStatement((DoWhileStatement) statement);break;
            case "ForStatement": executeForStatement((ForStatement) statement);break;
            case "FuncCallStatement": executeFuncCallStatement((FuncCallStatement) statement);break;
            case "BreakStatement": executeBreakStatement();break;
            case "ContinueStatement": executeContinueStatement();break;
            case "ReturnStatement": executeReturnStatement((ReturnStatement) statement);break;
            case "BlockStatement": executeBlock(((BlockStatement)statement));break;
            default:throw new Exception("statement type is not supported");
        }
    }

    /**
     * check mark
     */
    private boolean shouldReturn() {return returned;}
    private boolean shouldContinue() {return inCircle() && continueLevel == circleLevel;}
    private boolean shouldBreak() {return inCircle() && breakLevel == circleLevel; }

    /**
     * block 里面定义的变量存在新的env里面
     */
    private void enterBlock(){
       Env temp = new Env();
       temp.setLast(env);
       env = temp;
    }

    /**
     *离开block时，里面定义的局部变量销毁
     */
    private void exitBlock(){
        env = env.last;
    }

    private void executeBlock(BlockStatement blockStatement) throws Exception {
        enterBlock();
        for (Statement statement : blockStatement.block) {
            executeStatement(statement);
            if (shouldReturn() || shouldBreak() || shouldContinue()) {
               break;
            }
        }
        exitBlock();
    }

    private void executeReturnStatement(ReturnStatement returnStatement) throws Exception {
        returned = true;
        if (returnStatement.expr != null) {
            returnValue = executeExpr(returnStatement.expr);
        }
    }

    private void executeContinueStatement() throws Exception {
        if (inCircle()) {
            continueLevel = circleLevel;
        } else {
            throw new Exception("continue in an error position");
        }
    }

    private void executeBreakStatement() throws Exception {
        if (inCircle()) {
            breakLevel = circleLevel;
        } else {
            throw new Exception("break in an error position");
        }
    }

    private void executeFuncCallStatement(FuncCallStatement funcCallStatement) throws Exception {
        executeExpr(funcCallStatement.funcCallExpr);
    }
    private void executeWhileStatement(WhileStatement whileStatement) throws Exception {
        circleLevel++;
        Expr cond = whileStatement.condition;
        while (executeExpr(cond).i == TRUE) {
            executeBlock(whileStatement.block);
            if (shouldReturn()) {
                return;
            }
            if (shouldBreak()) {
                breakLevel = CLEAR_MARK;
                break;
            }
            if (shouldContinue()) {
                continueLevel = CLEAR_MARK;
            }
        }
        circleLevel--;
    }

    private void executeDoStatement(DoWhileStatement doWhileStatement) throws Exception {
        Expr cond = doWhileStatement.cond;
        circleLevel++;
        do {
            executeBlock(doWhileStatement.block);
            if (shouldReturn()) {
                return;
            }
            if (shouldBreak()) {
                breakLevel =CLEAR_MARK;
                break;
            }
            if (shouldContinue()) {
                continueLevel = CLEAR_MARK;
            }
        } while (executeExpr(cond).i == TRUE);
        circleLevel--;
    }

    private void executeForStatement(ForStatement forStatement) throws Exception {
        circleLevel++;
        if (forStatement.statement != null) {
            executeStatement(forStatement.statement);
        }
        Expr cond = forStatement.condition1Expr;
        while (executeExpr(cond).i == TRUE) {
            executeBlock(forStatement.block);
            if (shouldReturn()) {
                return;
            }
            if (shouldBreak()) {
                breakLevel = CLEAR_MARK;
                break;
            }
            executeStatement(forStatement.statement2);
            if (shouldContinue()) {
                continueLevel = CLEAR_MARK;
            }
        }
        circleLevel--;
    }

    private void executeIfStatement(IfStatement statement) throws Exception {
        if (executeExpr(statement.ifCond).i == TRUE) {
            executeBlock(statement.ifBlock);
            return;
        }
        for (int i = 0; i < statement.elseIfConds.size(); i++) {
            if (executeExpr(statement.elseIfConds.get(i)).i ==TRUE) {
                executeBlock(statement.elseIfBlocks.get(i));
                return;
            }
        }
        if (statement.elseBlock != null) {
            executeBlock(statement.elseBlock);
        }
    }

    private void executeAssignStatement(AssignStatement assignStatement) throws Exception {

        LocalVar var = executeExpr(assignStatement.right);
        Expr left = assignStatement.left;
        //变量赋值
        if (left instanceof IdExpr) {
            env.put(((IdExpr) left).id, var);
        } else {
            //数组赋值
            ArrayUse arrayUse = (ArrayUse) assignStatement.left;
            LocalVar array = env.get(arrayUse.arrayId);
            int index = executeExpr(arrayUse.index).i;
            if (array.type == VarType.INT_ARRAY) {
                array.ints[index] = var.i;
            } else {
                array.strs[index] = var.str;
            }
        }
    }

    private void executeDefineStatement(DefineStatement defineStatement) throws Exception {
        for(DefineStatement.DefineSingleStatement defineSingleStatement : defineStatement.singleStatements) {
            LocalVar localVar;
            if (defineSingleStatement.right != null) {
                localVar = executeExpr(defineSingleStatement.right);
            } else {
                localVar = new LocalVar();
                localVar.type = defineSingleStatement.type;
                if (localVar.type == VarType.INT_ARRAY) {
                    localVar.ints = new int[defineSingleStatement.size];
                } else if (localVar.type == VarType.STRING_ARRAY) {
                    localVar.strs = new String[defineSingleStatement.size];
                }
            }
            if (defineSingleStatement.type != localVar.type) {
                throw new Exception("type is not same");
            }
            env.putNewVar(defineSingleStatement.id, localVar);
        }
    }

    private LocalVar executeStringExpr(StringExpr expr){
        LocalVar localVar = new LocalVar();
        localVar.type = VarType.STRING;
        localVar.str = expr.str;
        return localVar;
    }
    private LocalVar executeIntExpr(IntExpr expr){
        LocalVar localVar = new LocalVar();
        localVar.type = VarType.INT;
        localVar.i = expr.i;
        return localVar;
    }
    private LocalVar executeIdExpr(IdExpr idExpr){
        return env.get(idExpr.id);
    }
    private LocalVar executeCalExpr(CalExpr calExpr) throws Exception {
        LocalVar localVar = new LocalVar();
        localVar.type = VarType.INT;
        LocalVar left = executeExpr(calExpr.left);
        LocalVar right = executeExpr(calExpr.right);
        if (left.type != VarType.INT || right.type != VarType.INT) {
            throw new Exception("error operator num type");
        }
        int result;
        switch (calExpr.op) {
            case "+": result = left.i + right.i;break;
            case "-":
                result = left.i - right.i;
                break;
            case "*": result = left.i * right.i;break;
            case "/":
                if (right.i == 0) throw new Exception("0 is div");
                result = left.i / right.i;break;
            case "%": result = left.i % right.i;break;
            case "|": result = left.i | right.i;break;
            case "&": result = left.i & right.i;break;
            default:
                throw new Exception("error operator");
        }
        localVar.i = result;
        return localVar;
    }
    private LocalVar executeRelExpr(RelExpr expr) throws Exception {
        LocalVar localVar = new LocalVar();
        localVar.type = VarType.INT;
        LocalVar left = executeExpr(expr.left);
        LocalVar right = executeExpr(expr.right);
        if (left.type != VarType.INT || right.type != VarType.INT) {
            throw new Exception("error operator num type");
        }
        boolean result;
        switch (expr.op) {
            case "==": result = left.i == right.i;break;
            case ">=": result = left.i >= right.i;break;
            case ">": result = left.i > right.i;break;
            case "<": result = left.i < right.i;break;
            case "<=": result = left.i <= right.i;break;
            case "!=":result = left.i != right.i;break;
            default:
                throw new Exception("error operator");
        }
        localVar.i = result ? TRUE : FALSE;
        return localVar;
    }
    private LocalVar executeNotExpr(NotExpr notExpr) throws Exception {
        LocalVar var = executeExpr(notExpr.expr);
        if(var.i == FALSE){
            var.i = TRUE;
        } else{
            var.i = FALSE;
        }
        return var;
    }
    private LocalVar executeBoolExpr(BoolExpr expr) throws Exception {
        LocalVar localVar = new LocalVar();
        localVar.type = VarType.INT;
        LocalVar left = executeExpr(expr.left);
        if (left.type != VarType.INT) {
            throw new Exception("error operator num type");
        }
        boolean result = left.i != FALSE;
        if (expr.op.equals("&&")) {
            LocalVar right = executeExpr(expr.right);
            if (right.type != VarType.INT) {
                throw new Exception("error operator num type");
            }
            result = result && right.i != FALSE;
        }
        localVar.i = result ? TRUE : FALSE;
        return localVar;
    }
    private LocalVar executeArrayUse(ArrayUse expr) throws Exception {
        LocalVar localVar = new LocalVar();
        LocalVar index = executeExpr(expr.index);
        if (index.type != VarType.INT) {
            throw new Exception("error array index type");
        }
        String id = expr.arrayId;
        LocalVar array = env.get(id);
        if (array.type == VarType.INT_ARRAY) {
            localVar.type = VarType.INT;
            localVar.i = array.ints[index.i];
        } else {
            localVar.type = VarType.STRING;
            localVar.str = array.strs[index.i];
        }
        return localVar;
    }
    private LocalVar executeArrayInit(ArrayInit expr){
        LocalVar localVar = new LocalVar();
        List<Expr> exprs = expr.exprs;
        //first expr's type means array's type
        if (exprs.size() != 0) {
            if (exprs.get(0) instanceof IntExpr) {
                localVar.type = VarType.INT_ARRAY;
                int[] ints = new int[exprs.size()];
                for (int i = 0; i < exprs.size(); i++) {
                    ints[i] = ((IntExpr) exprs.get(i)).i;
                }
                localVar.ints = ints;
            } else {
                localVar.type = VarType.STRING_ARRAY;
                String[] strs = new String[exprs.size()];
                for (int i = 0; i < exprs.size(); i++) {
                    strs[i] = ((StringExpr) exprs.get(i)).str;
                    localVar.strs = strs;
                }
            }
        }
        return localVar;
    }
    private LocalVar executeFuncCall(FuncCallExpr expr) throws Exception {
        List<Expr> exprs = expr.args;
        List<LocalVar> actualVars = new ArrayList<>();
        for (Expr value : exprs) {
            actualVars.add(executeExpr(value));
        }
        FuncStatement funcStatement = userFuncs.get(expr.funcName);
        //native method call
        if (funcStatement == null) {
            return NativeCall.execute(expr.funcName, actualVars);
        }

        List<ArgExpr> args = funcStatement.args;
        Env funEnv = new Env();
        //init function vars
        for (int i = 0; i < args.size(); i++) {
            funEnv.putNewVar(args.get(i).id, actualVars.get(i));
        }
        return new Executor(funEnv, funcStatement).execute();
    }
    /**
     * 执行表达式返回 localVar对象
     */
    private LocalVar executeExpr(Expr expr) throws Exception {
        String name = expr.getClass().getSimpleName();
        switch (name){
            case "StringExpr":return executeStringExpr((StringExpr) expr);
            case "IntExpr":return executeIntExpr((IntExpr) expr);
            case "IdExpr":return executeIdExpr((IdExpr) expr);
            case "CalExpr":return executeCalExpr((CalExpr) expr);
            case "RelExpr":return executeRelExpr((RelExpr) expr);
            case "NotExpr":return executeNotExpr((NotExpr) expr);
            case "BoolExpr":return executeBoolExpr((BoolExpr)expr);
            case "ArrayUse": return executeArrayUse((ArrayUse)expr);
            case "ArrayInit":return executeArrayInit((ArrayInit)expr);
            case "FuncCallExpr":return executeFuncCall((FuncCallExpr)expr);
            default:throw new Exception("expr can't be recognized");
        }
    }

    static class LocalVar {
        int type;
        int i;
        String str;
        int[] ints;
        String[] strs;
    }
}
