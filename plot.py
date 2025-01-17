import os
import sys
import pandas as pd
import numpy as np
import matplotlib
import plotnine
from plotnine import data
from plotnine import *
from dataclasses import dataclass
from mizani.formatters import comma_format

@dataclass
class Config:
    root_dir_name: str
    out_dir_name: str
    save_results: bool
    show_results: bool
    force_read: bool
    pdf_width: int
    pdf_height: int

    def __init__(self, argv):
        self.show_results = False
        self.save_results = False
        self.force_read = False
        self.pdf_width = 400
        self.pdf_height = 200

        for arg in argv[1:]:
            match arg:
                case '--show':
                    self.show_results = True
                case '--save':
                    self.save_results = True
                case '--force':
                    self.force_read = True

        if os.path.exists('results/.current'):
            with open('results/.current') as f:
                data_dir_name = f.readline().strip()
                self.root_dir_name = 'results/' + data_dir_name + '/'
        else:
            self.root_dir_name = 'data/'

        self.out_dir_name = self.root_dir_name + '/plot/'
        print(self.root_dir_name)


def readCSVs(file_name, dtype_spec):
    data_files = []
    for dirpath, _, filenames in os.walk(config.root_dir_name + '/data'):
        if file_name in filenames:
            data_files.append(os.path.join(dirpath, file_name))

    print(data_files)
    data_frames = [pd.read_csv(file, dtype=dtype_spec, sep = ',') for file in data_files]
    combined_data_frame = pd.concat(data_frames, ignore_index=True)
    combined_data_frame = combined_data_frame.drop_duplicates()
    return combined_data_frame


def set_graphics_options():
    pd.set_option('display.max_columns', None)
    pd.set_option('display.max_rows', None)
    pd.set_option('display.max_colwidth', None)

    font = {'size'   : 34}
    matplotlib.rc('font', **font)


def plot_general_coverage(df, config):
    df = df[df['Type'].isin(['reduced', 'twise'])]
    df = df[df['Error'] == False]
    df = df[df['Timeout'] == False]
    df = df[df['Type_org'] == 'field']
    df['CoverageType'] = df['CoverageType'].replace({'field_1': 'Field (t=1)', \
                     'field_2': 'Field (t=2)', \
                     'twise_1': 'T-Wise (t=1)', \
                     'twise_2': 'T-Wise (t=2)'})

    p = (
    ggplot(df, aes('CoverageType', 'Coverage', fill='ReduceType'))
    + geom_boxplot()
    + theme(legend_position='right', legend_title_align='left')
    + labs(x='Coverage Metric', y='Coverage', fill='ReduceType')
    )
    create_plot('coverage_per_coverage_type', p, config, 1)


def plot_coverage_box(df):
    df = df.copy()

    df = df[df['Error'] == False]
    df = df[df['Timeout'] == False]

    df = df[df['SampleTypeDisplay1'].isin(['Field (Scoring)',
                                   'Field (Set-Cover)',
                                   'Field (Random)',
                                   'YASA',
                                   'Combined (Scoring)',
                                   'Combined (Set-Cover)',
                                   'Combined (Random)'
                                   ])]

    df['CoverageMetricDisplay'] = df['CoverageMetric'].replace({'f_1': '$1$-Wise Fieldcoverage', \
                     'f_2': '$2$-Wise Fieldcoverage', \
                     't_1': '$1$-Wise-Coverage', \
                     't_2': '$2$-Wise-Coverage'})

    df['TT'] = df['T'].astype(str)
    df['TT'] = df['TT'].replace({'1': '$t = 1$', \
            '2': '$t = 2$'})

    p = (
    ggplot(df, aes('SampleTypeDisplay1', 'Coverage', fill='TT'))
    + geom_boxplot()
    + theme(legend_position='top', axis_text_x=element_text(rotation=20, hjust=1))
    + labs(x='Sample Type', y='Coverage', fill='t-value used for reduction/sampling')
    + facet_wrap('CoverageMetricDisplay')
    )
    create_plot('coverage_per_sample_type_and_coverage_type', p, config, 1)


def plot_size_per_sample_type_box(df):
    df = df.copy()

    df = df[df['Error'] == False]
    df = df[df['Timeout'] == False]

    df = df[df['SampleTypeDisplay1'].isin(['Field (Scoring)',
                                   'Field (Set-Cover)',
                                   'Field (Random)',
                                   'YASA',
                                   'Combined (Scoring)',
                                   'Combined (Set-Cover)',
                                   'Combined (Random)'
                                   ])]

    df['CoverageMetricDisplay'] = df['CoverageMetric'].replace({'f_1': '$1$-Wise Fieldcoverage', \
                     'f_2': '$2$-Wise Fieldcoverage', \
                     't_1': '$1$-Wise-Coverage', \
                     't_2': '$2$-Wise-Coverage'})

    df['TT'] = df['T'].astype(str)
    df['TT'] = df['TT'].replace({'1': '$t = 1$', \
            '2': '$t = 2$'})

    p = (
    ggplot(df, aes('SampleTypeDisplay1', 'Size', fill='TT'))
    + geom_boxplot()
    + scale_y_log10(labels=comma_format())
    + theme(legend_position='top', axis_text_x=element_text(rotation=20, hjust=1))
    + labs(x='Sample Type', y='Sample Size', fill='t-value used for reduction/sampling')
    )
    create_plot('size_per_sample_type', p, config, 1)


def plot_size_per_t_box(df):
    df = df.copy()

    df = df[df['Error'] == False]
    df = df[df['Timeout'] == False]

    df = df[df['SampleTypeDisplay1'].isin(['Field (Scoring)',
                                   'Field (Set-Cover)',
                                   'Field (Random)',
                                   'YASA',
                                   'Combined (Scoring)',
                                   'Combined (Set-Cover)',
                                   'Combined (Random)'
                                   ])]

    df['CoverageMetricDisplay'] = df['CoverageMetric'].replace({'f_1': '$1$-Wise Fieldcoverage', \
                     'f_2': '$2$-Wise Fieldcoverage', \
                     't_1': '$1$-Wise-Coverage', \
                     't_2': '$2$-Wise-Coverage'})

    df['TT'] = df['T'].astype(str)
    df['TT'] = df['TT'].replace({'1': '$t = 1$', \
            '2': '$t = 2$'})

    p = (
    ggplot(df, aes('TT', 'Size', fill='SampleTypeDisplay1'))
    + geom_boxplot()
    + scale_y_log10(labels=comma_format())
    + theme(legend_position='top', figure_size=(6, 4))
    + labs(x='t-value used for reduction/sampling', y='Sample Size', fill='')
    )
    create_plot('size_per_t', p, config, 1)



def plot_reduced_size_scatter(df):
    df = df.copy()

    df = df[df['CoverageMetric'].str.startswith('f_1')]
    #df = df[df['Type'] == 'r']

    df = df[df['Error'] == False]
    df = df[df['Timeout'] == False]

    df = df[df['Type_org'] == 'f']
    df = df[df['Algorithm'].isin(['BitSetScoring1SampleReducer',
                                'BitSetCounterSampleReducer',
                                'RandomSampleReducer'
                                ])]
    df['AlgorithmText'] = df['Algorithm'].replace({
                     'BitSetScoring1SampleReducer': 'Scoring',
                     'RandomSampleReducer': 'Random',
                     'BitSetCounterSampleReducer': 'Set-Cover'})

    df['TT'] = df['T'].astype(str)
    df['TT'] = df['TT'].replace({'1': '$t = 1$', \
            '2': '$t = 2$'})

    p = (
    ggplot(df, aes(x='Size_org', y='Size', shape='AlgorithmText', fill='AlgorithmText'))
    + geom_point(size=3.5)
    #+ geom_line(aes(group='SampleTypeDisplay1', color='SampleTypeDisplay1'), linetype='dashed', show_legend=False)
    + scale_x_log10(labels=comma_format())
    + scale_y_log10(labels=comma_format())
   # + theme(legend_position='right', legend_title_align='left')
    + labs(x='Original Sample Size', y='Reduced Sample Size', fill='Reduce\nAlgorithm\n\n', shape='Reduce\nAlgorithm\n\n')
    + facet_wrap('TT')
    )
    create_plot('new_size_per_org_size_and_reduction_type', p, config, 1)


def plot_time_scatter_systems(df):
    df = df.copy()

    df = df[df['Error'] == False]
    #df = df[df['Timeout'] == False]

    df = df[df['CoverageMetric'].str.startswith('f_1')]
    df = df[df['SampleTypeDisplay1'].isin(['Field (Scoring)',
                                   'Field (Set-Cover)',
                                   'Field (Random)',
                                   'YASA',
                                   'Combined (Scoring)',
                                   'Combined (Set-Cover)',
                                   'Combined (Random)',
                                   ])]

    df['TT'] = df['T'].astype(str)
    df['TT'] = df['TT'].replace({'1': '$t = 1$', \
            '2': '$t = 2$'})

    p = (
    ggplot(df, aes(x='SystemName', y='Time', shape='SampleTypeDisplay1', fill='SampleTypeDisplay1'))
    + geom_jitter(width=0.3, height=0, size=3.5)
    #+ geom_line(aes(group='SampleTypeDisplay1', color='SampleTypeDisplay1'), linetype='dashed', show_legend=False)
    + scale_y_log10(labels=comma_format())
    + theme(legend_position='top', axis_text_x=element_text(rotation=45, hjust=1))
    + labs(x='System Name', y='Sampling Time (in seconds)', fill='', shape='')
    + facet_wrap('TT')
    )
    create_plot('time_per_system_and_sample_type', p, config, 1)


def plot_size_scatter_systems(df):
    df = df.copy()

    df = df[df['Error'] == False]
    df = df[df['Timeout'] == False]

    df = df[df['CoverageMetric'].str.startswith('f_1')]
    df = df[df['SampleTypeDisplay1'].isin(['Field (Scoring)',
                                   'Field (Set-Cover)',
                                   'Field (Random)',
                                   'YASA',
                                   'Combined (Scoring)',
                                   'Combined (Set-Cover)',
                                   'Combined (Random)',
                                   ])]

    df['TT'] = df['T'].astype(str)
    df['TT'] = df['TT'].replace({'1': '$t = 1$', \
            '2': '$t = 2$'})

    p = (
    ggplot(df, aes('SystemName', 'Size', shape='SampleTypeDisplay1', fill='SampleTypeDisplay1'))
    + geom_jitter(width=0.3, height=0, size=3.5)
    #+ geom_line(aes(group='SampleTypeDisplay1', color='SampleTypeDisplay1'), linetype='dashed', show_legend=False)
    + scale_y_log10(labels=comma_format())
    + theme(legend_position='top', axis_text_x=element_text(rotation=45, hjust=1))
    + labs(x='System Name', y='Sample Size', fill='', shape='')
    + facet_wrap('TT')
    )
    create_plot('size_per_system_and_sample_type', p, config, 1)


def create_out_dir():
    if not os.path.exists(config.out_dir_name):
        try:
            os.mkdir(config.out_dir_name)
        except OSError:
            print ('Failed to create output directory %s' % output_path)
            os.exit(-1)


def create_plot(name, p, config, factor):
    if config.show_results:
        p.show()

    if config.save_results:
        create_out_dir()
        file_name = config.out_dir_name + name + '.pdf'
        print('Writing ' + file_name)
        p.save(file_name)


def create_csv(df, name):
    if config.show_results:
        print(df)

    if config.save_results:
        create_out_dir()
        df.to_csv(config.out_dir_name + name + '.csv', index=False, sep = ';')


def create_table(df, name, config):
    table = df.style.format(decimal='.', thousands=',', precision=2, escape='latex').to_latex(multicol_align='c')

    if config.show_results:
        print(table)

    if config.save_results:
        create_out_dir()
        with open(config.out_dir_name + name + '.tex', 'w') as f:
            print(table, file=f)


def top(series):
    return series.iloc[0]



def prepare_data():
    dtype_systems = {
        'ID': 'int16',
        'Name': 'string',
    }

    dtype_models = {
        'ID': 'int16',
        'SystemID': 'int16',
        'Version': 'string',
        'VariableCount': 'Int32',
        'ClauseCount': 'Int32',
    }

    dtype_samples = {
        'ID': 'int16',
        'SystemID': 'int16',
        'ModelID': 'int16',
        'Path': 'string',
        'Type': 'string',
        'Algorithm': 'string',
        'AlgorithmIt': 'int8',
        'T': 'int8',
        'Size': 'int64',
        'Time': 'int64',
        'OriginalID': 'int16',
        'Error': 'bool',
        'Timeout': 'bool',
        'Seed': 'int64',
    }

    dtype_coverage = {
        'SampleID': 'int16',
        'VariableCount': 'int32',
        'CoverageType': 'string',
        'T': 'Int8',
        'Coverage': 'float64',
    }

    if config.force_read or not os.path.exists(config.out_dir_name + 'complete.pkl'):
        print('Reading and joining original tables')

        systems = readCSVs('systems.csv', dtype_systems).set_index('ID')
        systems = systems.rename(columns={'Name': 'SystemName'})

        models = readCSVs('models.csv', dtype_models).drop(['SystemID'], axis=1).set_index('ID')
        models = models.rename(columns={'VariableCount': 'ModelVariableCount'})

        coverage = readCSVs('coverage.csv', dtype_coverage).rename(columns={'T': 'CoverageT', 'VariableCount': 'CoverageVariableCount'})
        coverage['CoverageMetric']  = coverage['CoverageType'] + '_' + coverage['CoverageT'].astype('string')

        samples = readCSVs('samples.csv', dtype_samples)

        samples['Algorithm2'] = samples['Algorithm'].fillna(samples['Path'])
        samples['Algorithm'] = samples['Algorithm'].fillna(samples['Type'])

        samples['Time'] = (samples['Time'] / 1000000) / 1000.0

        samples.loc[samples['Timeout'], 'Time'] = 3600
        samples.loc[samples['Error'], 'Time'] = np.nan
        samples.loc[(samples['Timeout'] == False) & (samples['Error'] == False) & (samples['Time'] < 0), 'Time'] = np.nan
        samples.loc[samples['Timeout'] | samples['Error'], 'Size'] = np.nan

        samples['T'] = samples['T'].fillna(0)
        samples.loc[samples['Type'] == 'c', 'T'] = samples['Path'].str.strip().str[-1]
        samples['T'] = samples['T'].astype('int8')

        samples = samples.reset_index()
        data = pd.merge(samples, pd.Series(coverage['CoverageMetric'].unique(), name='CoverageMetric'), how='cross')
        data = pd.merge(data, coverage, left_on=['ID', 'CoverageMetric'], right_on=['SampleID', 'CoverageMetric'], how='left')

        data = data.groupby(['SystemID', 'ModelID', 'Type', 'OriginalID', 'Algorithm2', 'T', 'CoverageMetric'], observed=True).agg({
            'ID': top,
            'Path': top,
            'Algorithm': top,
            'Size': 'median',
            'Time': 'median',
            'Error': top,
            'Timeout': top,
            'Seed': top,
            'CoverageVariableCount': top,
            'CoverageT': top,
            'CoverageType': top,
            'Coverage': 'median'
        }).reset_index().set_index('ID')

        data = data \
                    .join(systems, on='SystemID') \
                    .join(models, on='ModelID')
        data = data.reset_index()
        data = data.rename(columns={'ID': 'SampleID'})

        data['SampleType']      = data['Type'] + '_' + data['Algorithm'] + '_' + data['T'].astype('string')
        data['Reduced']         = data['Type'].str.contains('r')

        data = pd.merge(data, data, left_on=['OriginalID', 'CoverageMetric'], right_on=['SampleID', 'CoverageMetric'], how='left', suffixes=('', '_org'))
        data = data.reset_index()

        data['SizeDiff'] = data['Size_org'] - data['Size']
        data['SizeRatio'] = 1 - (data['Size'] / data['Size_org'])
        data['TimeDiff'] = data['Time_org'] - data['Time']
        data['TimeRatio'] = 1 - (data['Time'] / data['Time_org'])
        data['CoverageDiff'] = data['Coverage_org'] - data['Coverage']
        data['CoverageRatio'] = 1 - (data['Coverage'] / data['Coverage_org'])

        data['CompleteType'] = data['SampleType'] + '__' + data['SampleType_org'].astype(str)

        data['SampleTypeDisplay1'] = data['CompleteType'].replace({
                    'r_BitSetScoring1SampleReducer_1__f_f_0': 'Field (Scoring)', \
                    'r_BitSetScoring1SampleReducer_2__f_f_0': 'Field (Scoring)', \
                    'r_BitSetCounterSampleReducer_1__f_f_0': 'Field (Set-Cover)', \
                    'r_BitSetCounterSampleReducer_2__f_f_0': 'Field (Set-Cover)', \
                    'r_RandomSampleReducer_2__f_f_0': 'Field (Random)', \
                    'r_RandomSampleReducer_1__f_f_0': 'Field (Random)', \
                    't_yasa_1__<NA>': 'YASA', \
                    't_yasa_2__<NA>': 'YASA', \
                    'r_BitSetScoring1SampleReducer_1__c_c_1': 'Combined (Scoring)', \
                    'r_BitSetScoring1SampleReducer_2__c_c_2': 'Combined (Scoring)', \
                    'r_BitSetCounterSampleReducer_1__c_c_1': 'Combined (Set-Cover)', \
                    'r_BitSetCounterSampleReducer_2__c_c_2': 'Combined (Set-Cover)', \
                    'r_RandomSampleReducer_1__c_c_1': 'Combined (Random)', \
                    'r_RandomSampleReducer_2__c_c_2': 'Combined (Random)'})

        data['SampleTypeDisplay2'] = data['CompleteType'].replace({
                    'r_BitSetScoring1SampleReducer_1__f_f_0': 'Field (Scoring, $t=1$)', \
                    'r_BitSetScoring1SampleReducer_2__f_f_0': 'Field (Scoring, $t=2$)', \
                    'r_BitSetCounterSampleReducer_1__f_f_0': 'Field (Set-Cover, $t=1$)', \
                    'r_BitSetCounterSampleReducer_2__f_f_0': 'Field (Set-Cover, $t=2$)', \
                    'r_RandomSampleReducer_2__f_f_0': 'Field (Random, $t=1$)', \
                    'r_RandomSampleReducer_1__f_f_0': 'Field (Random, $t=2$)', \
                    't_yasa_1__<NA>': 'YASA ($t=1$)', \
                    't_yasa_2__<NA>': 'YASA ($t=2$)', \
                    'r_BitSetScoring1SampleReducer_1__c_c_1': 'Combined (Scoring, $t=1$)', \
                    'r_BitSetScoring1SampleReducer_2__c_c_2': 'Combined (Scoring, $t=2$)', \
                    'r_BitSetCounterSampleReducer_1__c_c_1': 'Combined (Set-Cover, $t=1$)', \
                    'r_BitSetCounterSampleReducer_2__c_c_2': 'Combined (Set-Cover, $t=2$)', \
                    'r_RandomSampleReducer_1__c_c_1': 'Combined (Random, $t=1$)', \
                    'r_RandomSampleReducer_2__c_c_2': 'Combined (Random, $t=2$)'})

        data = data[['SampleID', 'SystemID', 'ModelID', 'SystemName', 'Version', 'ModelVariableCount', 'ClauseCount', 'SampleType', 'CompleteType', 'SampleTypeDisplay1', 'SampleTypeDisplay2', 'SampleType_org', 'Type', 'Type_org', 'Algorithm', 'T', 'Reduced', 'CoverageVariableCount', 'Size', 'Time', 'CoverageMetric', 'CoverageType', 'CoverageT', 'Coverage', 'CoverageVariableCount_org', 'Size_org', 'Time_org', 'Coverage_org', 'SizeDiff', 'SizeRatio', 'TimeDiff', 'TimeRatio', 'CoverageDiff', 'CoverageRatio', 'Path', 'OriginalID', 'Error', 'Timeout']]

        data = data.sort_values(by=['SystemID', 'ModelID', 'Version', 'CompleteType', 'CoverageMetric'])

        data = data[(data['SystemName'].str.startswith('Automotive') == False) | (data['Type'] != 't') | (data['Type'] != 'f') | ((data['Type'] == 'r') & (data['Type_org'] != 'c'))]

        print('Writing complete table')
        create_out_dir()

    print('Reading complete table')

    print('========================================')
    print(data.head(30))
    data.info(verbose=True, memory_usage='deep')
    print('========================================')

    return data


def get_origin_from_path(p):
    dir_name, file_name = os.path.split(p)

    dir_names = []
    while dir_name != '':
        dir_names.append(os.path.basename(dir_name))
        dir_name = os.path.dirname(dir_name)


    dir_names.reverse()
    if len(dir_names) == 2:
        dir_names.pop(1)
    try:
        index = file_name.index('_reduced')
        dir_names.append(file_name[0:index])
    except ValueError:
        dir_names.append(file_name[:-4])
        print('_'.join(dir_names))
    return '_'.join(dir_names)


if __name__ == '__main__':
    config = Config(sys.argv)
    set_graphics_options()
    data = prepare_data()

    plot_time_scatter_systems(data)
    plot_size_scatter_systems(data)
    plot_reduced_size_scatter(data)
    plot_size_per_sample_type_box(data)
    plot_size_per_t_box(data)
    plot_coverage_box(data)
