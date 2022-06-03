package cc.calliope.mini;

import androidx.recyclerview.widget.DefaultItemAnimator;

public class DefaultItemAnimatorNoChange extends DefaultItemAnimator {
    public DefaultItemAnimatorNoChange() {
        setSupportsChangeAnimations(false);
    }
}
