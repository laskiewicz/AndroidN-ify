package tk.wasdennnoch.androidn_ify.systemui.notifications.stack;

import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import tk.wasdennnoch.androidn_ify.R;
import tk.wasdennnoch.androidn_ify.XposedHook;
import tk.wasdennnoch.androidn_ify.utils.ConfigUtils;
import tk.wasdennnoch.androidn_ify.utils.ResourceUtils;

public class StackScrollAlgorithmHooks {

    private static final String TAG = "StackScrollAlgorithmHooks";
    public static final int LOCATION_TOP_STACK_HIDDEN = 0x02;
    private static final Rect mClipBounds = new Rect();
    public static ViewGroup mStackScrollLayout;
    private static float mStackTop = 0;
    private static float mStateTop = 0;

    private static Field fieldCollapsedSize;
    private static Field fieldVisibleChildren;
    private static Field fieldScrollY;
    private static Field fieldShadeExpanded;
    private static Field fieldHeadsUpHeight;
    private static Field fieldTopPadding;
    private static Field fieldStackTranslation;
    private static Field fieldYTranslation;
    private static Field fieldLocation;

    private static Method methodGetViewStateForView;
    private static Method methodGetTopHeadsUpEntry;

    public static void hook(ClassLoader classLoader) {
        try {
            ConfigUtils config = ConfigUtils.getInstance();

            Class classNotificationStackScrollLayout = XposedHelpers.findClass("com.android.systemui.statusbar.stack.NotificationStackScrollLayout", classLoader);
            XposedBridge.hookAllMethods(classNotificationStackScrollLayout, "initView", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mStackScrollLayout = (ViewGroup) param.thisObject;
                }
            });

            Class classStackScrollAlgorithm = XposedHelpers.findClass("com.android.systemui.statusbar.stack.StackScrollAlgorithm", classLoader);
            XposedHelpers.findAndHookMethod(classStackScrollAlgorithm, "initConstants", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    int mZDistanceBetweenElements = ResourceUtils.getInstance((Context) param.args[0])
                            .getDimensionPixelSize(R.dimen.z_distance_between_notifications);
                    int mZBasicHeight = 4 * mZDistanceBetweenElements;
                    XposedHelpers.setIntField(param.thisObject, "mZDistanceBetweenElements", mZDistanceBetweenElements);
                    XposedHelpers.setIntField(param.thisObject, "mZBasicHeight", mZBasicHeight);
                }
            });

            XposedBridge.hookAllMethods(classNotificationStackScrollLayout, "updateChildren", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            //if (((mStackTop == mStateTop || mStackScrollLayout == null) && config.notifications.experimental)) return;
                            mClipBounds.right = mStackScrollLayout.getWidth();
                            mClipBounds.left = 0;
                            mClipBounds.top = (int)mStackScrollLayout.getY();
                            mClipBounds.bottom = Integer.MAX_VALUE;
                            mStackScrollLayout.setClipBounds(mClipBounds);
                        }
                    }
            );

            if (!config.notifications.experimental) return;

            if (config.notifications.change_style) {
                Class classStackScrollAlgorithmState = XposedHelpers.findClass("com.android.systemui.statusbar.stack.StackScrollAlgorithm.StackScrollAlgorithmState", classLoader);
                Class classStackScrollState = XposedHelpers.findClass("com.android.systemui.statusbar.stack.StackScrollState", classLoader);
                Class classStackViewState = XposedHelpers.findClass("com.android.systemui.statusbar.stack.StackViewState", classLoader);
                Class classAmbientState = XposedHelpers.findClass("com.android.systemui.statusbar.stack.AmbientState", classLoader);
                Class classExpandableNotificationRow = XposedHelpers.findClass("com.android.systemui.statusbar.ExpandableNotificationRow", classLoader);

                fieldCollapsedSize = XposedHelpers.findField(classStackScrollAlgorithm, "mCollapsedSize");
                fieldVisibleChildren = XposedHelpers.findField(classStackScrollAlgorithmState, "visibleChildren");
                fieldScrollY = XposedHelpers.findField(classStackScrollAlgorithmState, "scrollY");
                fieldShadeExpanded = XposedHelpers.findField(classAmbientState, "mShadeExpanded");
                fieldHeadsUpHeight = XposedHelpers.findField(classExpandableNotificationRow, "mHeadsUpHeight");
                fieldTopPadding = XposedHelpers.findField(classAmbientState, "mTopPadding");
                fieldStackTranslation = XposedHelpers.findField(classAmbientState, "mStackTranslation");
                fieldYTranslation = XposedHelpers.findField(classStackViewState, "yTranslation");
                fieldLocation = XposedHelpers.findField(classStackViewState, "location");

                methodGetViewStateForView = XposedHelpers.findMethodBestMatch(classStackScrollState, "getViewStateForView", View.class);
                methodGetTopHeadsUpEntry = XposedHelpers.findMethodBestMatch(classAmbientState, "getTopHeadsUpEntry");

                XposedBridge.hookAllMethods(classStackScrollAlgorithm, "updateStateForTopStackChild", new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        Object childViewState = param.args[4];
                        float scrollOffset = (float) param.args[5];
                        updateStateForTopStackChild(childViewState, scrollOffset);
                        return null;
                    }
                });

                XposedBridge.hookAllMethods(classStackScrollAlgorithm, "updateHeadsUpStates", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object resultState = param.args[0];
                        Object algorithmState = param.args[1];
                        Object ambientState = param.args[2];
                        int mCollapsedSize = fieldCollapsedSize.getInt(param.thisObject);
                        updateHeadsUpStates(algorithmState, resultState, ambientState, mCollapsedSize);
                    }
                });

                XposedBridge.hookAllMethods(classStackScrollAlgorithm, "clampPositionToTopStackEnd", XC_MethodReplacement.DO_NOTHING);
                XposedBridge.hookAllMethods(classStackScrollAlgorithm, "findNumberOfItemsInTopStackAndUpdateState", XC_MethodReplacement.DO_NOTHING);

                XposedHelpers.findAndHookMethod(classNotificationStackScrollLayout, "onInterceptTouchEvent", MotionEvent.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        MotionEvent ev = (MotionEvent) param.args[0];
                        if (ev.getY() < mStateTop)
                            param.setResult(false);
                    }
                });
            }

        } catch (Throwable t) {
            XposedHook.logE(TAG, "Error hooking SystemUI", t);
        }
    }

    private static void updateStateForTopStackChild(Object childViewState, float scrollOffset) {
        setFloat(fieldYTranslation, childViewState, scrollOffset);
        setInt(fieldLocation, childViewState, LOCATION_TOP_STACK_HIDDEN);
    }

    private static void updateHeadsUpStates(Object algorithmState, Object resultState, Object ambientState, int mCollapsedSize) {
        List<?> visibleChildren = (List<?>) get(fieldVisibleChildren, algorithmState);
        if (visibleChildren == null || visibleChildren.size() < 1) {
            mStateTop = 0;
            return;
        }
        Object child = visibleChildren.get(0);
        Object childViewState = invoke(methodGetViewStateForView, resultState, child);

        int scrollY = getInt(fieldScrollY, algorithmState);

        Object topHeadsUpEntry = invoke(methodGetTopHeadsUpEntry, ambientState);
        boolean isShadeExpanded = getBoolean(fieldShadeExpanded, ambientState);

        float yTranslation = mCollapsedSize - scrollY;

        if (isShadeExpanded && topHeadsUpEntry != null
                && child != topHeadsUpEntry) {
            yTranslation += getInt(fieldHeadsUpHeight, topHeadsUpEntry) - mCollapsedSize;
        }

        mStateTop = getInt(fieldTopPadding, ambientState)
                + getFloat(fieldStackTranslation, ambientState);
        yTranslation += mStateTop;

        setFloat(fieldYTranslation, childViewState, yTranslation);
    }

    private static Object get(Field field, Object object) {
        try {
            return field.get(object);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int getInt(Field field, Object object) {
        try {
            return field.getInt(object);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static float getFloat(Field field, Object object) {
        try {
            return field.getFloat(object);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void setInt(Field field, Object object, int value) {
        try {
            field.setInt(object, value);
        } catch (Throwable ignore) {

        }
    }

    private static void setFloat(Field field, Object object, float value) {
        try {
            field.setFloat(object, value);
        } catch (Throwable ignore) {

        }
    }

    private static boolean getBoolean(Field field, Object object) {
        try {
            return field.getBoolean(object);
        } catch (Throwable t) {
            return false;
        }
    }

    private static Object invoke(Method method, Object object, Object... args) {
        try {
            return method.invoke(object, args);
        } catch (Throwable t) {
            return null;
        }
    }
}
