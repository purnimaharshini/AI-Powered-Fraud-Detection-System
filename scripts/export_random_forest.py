#!/usr/bin/env python3
import json
import pickle
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODEL_PATH = ROOT / "fraud_model.pkl"
OUTPUT_PATH = ROOT / "src" / "main" / "resources" / "model" / "fraud_model.json"


def export_tree(estimator):
    tree = estimator.tree_
    values = []
    for node_values in tree.value:
        class_counts = node_values[0].tolist()
        total = sum(class_counts) or 1.0
        values.append([count / total for count in class_counts])

    return {
        "childrenLeft": tree.children_left.tolist(),
        "childrenRight": tree.children_right.tolist(),
        "featureIndices": tree.feature.tolist(),
        "thresholds": tree.threshold.tolist(),
        "classProbabilities": values,
    }


def main():
    with MODEL_PATH.open("rb") as handle:
        model = pickle.load(handle)

    payload = {
        "featureNames": model.feature_names_in_.tolist(),
        "classes": model.classes_.tolist(),
        "trees": [export_tree(estimator) for estimator in model.estimators_],
    }

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT_PATH.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, separators=(",", ":"))

    print(f"Wrote {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
