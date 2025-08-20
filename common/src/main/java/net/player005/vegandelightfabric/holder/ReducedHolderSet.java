package net.player005.vegandelightfabric.holder;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.TagKey;
import java.util.List;
import java.util.Optional;

//matches all holders that are contained in the first set but not the second
public class ReducedHolderSet<T> extends HolderSet.ListBacked<T> {
    private final HolderSet<T> first;
    private final HolderSet<T> toRemove;
    private List<Holder<T>> cached;
    public ReducedHolderSet(HolderSet<T> first, HolderSet<T> toRemove) {
        this.first = first;
        this.toRemove = toRemove;
    }

    @Override
    protected List<Holder<T>> contents() {
        ensureCached();
        return cached;
    }

    private void ensureCached() {
        if (cached != null)
            return;
        cached = first.stream().filter(present -> !toRemove.contains(present)).toList();
    }

    @Override
    public boolean isBound() {
        return first.isBound() && toRemove.isBound();
    }
    public Either<TagKey<T>, List<Holder<T>>> unwrap() {
        return Either.right(this.cached);
    }

    public Optional<TagKey<T>> unwrapKey() {
        return Optional.empty();
    }

    @Override
    public boolean contains(Holder<T> holder) {
        ensureCached();
        return cached.contains(holder);
    }
}
