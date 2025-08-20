package net.player005.vegandelightfabric.holder;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

//matches all holders that are present in the first set or the second
public class OrHolderSet<T> extends HolderSet.ListBacked<T> {
    private final HolderSet<T> first;
    private final HolderSet<T> second;
    private List<Holder<T>> cached;
    public OrHolderSet(HolderSet<T> first, HolderSet<T> second) {
        this.first = first;
        this.second = second;
    }

    @Override
    protected List<Holder<T>> contents() {
        ensureCached();
        return cached;
    }

    private void ensureCached() {
        if (cached != null)
            return;
        cached = Stream.concat(first.stream(), second.stream()).toList();
    }

    @Override
    public boolean isBound() {
        return first.isBound() && second.isBound();
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
