import sys
from os import listdir
from pathlib import Path

import matplotlib.pyplot as plt
import pandas as pd


def find_csv_filenames(path_to_dir: str, suffix=".csv"):
    filenames = listdir(path_to_dir)
    return [path_to_dir + "/" + filename for filename in filenames if filename.endswith(suffix)]


def plot(data_path: str, x_axis: str, y_axis: str):
    plts = list()
    for filename in find_csv_filenames(data_path):
        data = pd.read_csv(filename)
        plts.append(plt.plot(data[x_axis], data[y_axis], "s", label=Path(filename).stem)[0])
    plt.legend(handles=plts)
    plt.xlabel(x_axis)
    plt.ylabel(y_axis)
    plt.savefig(f'{y_axis}.png')


if __name__ == '__main__':
    data_path = sys.argv[1]
    x_axis = sys.argv[2]
    y_axis = sys.argv[3]
    plot(data_path, x_axis, y_axis)
