package pl.exsio.nestedj.delegate.query.jpa;

import com.google.common.base.Preconditions;
import pl.exsio.nestedj.delegate.query.NestedNodeMovingQueryDelegate;
import pl.exsio.nestedj.discriminator.TreeDiscriminator;
import pl.exsio.nestedj.model.NestedNode;
import pl.exsio.nestedj.model.NestedNodeInfo;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.CriteriaUpdate;
import javax.persistence.criteria.Root;
import java.io.Serializable;
import java.util.List;

import static pl.exsio.nestedj.model.NestedNode.*;

public class JpaNestedNodeMovingQueryDelegateImpl<ID extends Serializable, N extends NestedNode<ID>>
        extends JpaNestedNodeQueryDelegate<ID, N>
        implements NestedNodeMovingQueryDelegate<ID, N> {

    private enum Mode {
        UP, DOWN
    }

    public JpaNestedNodeMovingQueryDelegateImpl(EntityManager entityManager, TreeDiscriminator<ID, N> treeDiscriminator, Class<N> nodeClass, Class<ID> idClass) {
        super(entityManager, treeDiscriminator, nodeClass, idClass);
    }

    @Override
    public List<ID> getNodeIds(NestedNodeInfo<ID, N> node) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ID> select = cb.createQuery(idClass);
        Root<N> root = select.from(node.getNodeClass());
        select.select(root.get(ID)).where(
                getPredicates(cb, root,
                        cb.greaterThanOrEqualTo(root.get(LEFT), node.getLeft()),
                        cb.lessThanOrEqualTo(root.get(RIGHT), node.getRight())
                ));
        return entityManager.createQuery(select).getResultList();
    }

    @Override
    public void updateFieldsUp(Long delta, Long start, Long stop, String field) {
        updateFields(Mode.UP, delta, start, stop, field);
    }

    @Override
    public void updateFieldsDown(Long delta, Long start, Long stop, String field) {
        updateFields(Mode.DOWN, delta, start, stop, field);
    }

    @Override
    public void performMoveUp(Long nodeDelta, List<ID> nodeIds, Long levelModificator) {
        performMove(Mode.UP, nodeDelta, nodeIds, levelModificator);
    }

    @Override
    public void performMoveDown(Long nodeDelta, List<ID> nodeIds, Long levelModificator) {
        performMove(Mode.DOWN, nodeDelta, nodeIds, levelModificator);
    }

    @Override
    public void updateParentField(ID newParentId, NestedNodeInfo<ID, N> node) {
        Preconditions.checkNotNull(newParentId);
        doUpdateParentField(newParentId, node);
    }

    @Override
    public void clearParentField(NestedNodeInfo<ID, N> node) {
        doUpdateParentField(null, node);
    }

    private void updateFields(Mode mode, Long delta, Long start, Long stop, String field) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaUpdate<N> update = cb.createCriteriaUpdate(nodeClass);
        Root<N> root = update.from(nodeClass);

        if (Mode.DOWN.equals(mode)) {
            update.set(root.<Long>get(field), cb.diff(root.get(field), delta));
        } else if (Mode.UP.equals(mode)) {
            update.set(root.<Long>get(field), cb.sum(root.get(field), delta));
        }
        update.where(getPredicates(cb, root,
                cb.greaterThan(root.get(field), start),
                cb.lessThan(root.get(field), stop)
        ));
        entityManager.createQuery(update).executeUpdate();
    }

    private void performMove(Mode mode, Long nodeDelta, List<ID> nodeIds, Long levelModificator) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaUpdate<N> update = cb.createCriteriaUpdate(nodeClass);
        Root<N> root = update.from(nodeClass);

        update.set(root.<Long>get(LEVEL), cb.sum(root.get(LEVEL), levelModificator));
        if (Mode.DOWN.equals(mode)) {
            update.set(root.<Long>get(RIGHT), cb.diff(root.get(RIGHT), nodeDelta));
            update.set(root.<Long>get(LEFT), cb.diff(root.get(LEFT), nodeDelta));
        } else if (Mode.UP.equals(mode)) {
            update.set(root.<Long>get(RIGHT), cb.sum(root.get(RIGHT), nodeDelta));
            update.set(root.<Long>get(LEFT), cb.sum(root.get(LEFT), nodeDelta));
        }
        update.where(
                getPredicates(cb, root, root.get(ID).in(nodeIds))
        );
        entityManager.createQuery(update).executeUpdate();
    }

    private void doUpdateParentField(ID newParentId, NestedNodeInfo<ID, N> node) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaUpdate<N> update = cb.createCriteriaUpdate(node.getNodeClass());
        Root<N> root = update.from(node.getNodeClass());

        update.set(root.get(PARENT_ID), newParentId)
                .where(getPredicates(cb, root, cb.equal(root.get(ID), node.getId())));

        entityManager.createQuery(update).executeUpdate();
    }
}